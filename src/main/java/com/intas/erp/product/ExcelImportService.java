package com.intas.erp.product;

import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Imports the shop's Go Plus stock export (.xls/.xlsx). Columns are matched by
 * their Turkish header names, folded to ASCII so casing/locale can't break the
 * match. Rows are upserted by product code; dummy invoice rows ("00-..") and
 * nameless rows are skipped, mirroring the site's catalog converter.
 */
@Service
public class ExcelImportService {

  public record Result(int imported, int updated, int skipped) {}

  private final ProductRepository repository;
  private final StockService stockService;
  private final DataFormatter formatter = new DataFormatter(new Locale("tr", "TR"));

  public ExcelImportService(ProductRepository repository, StockService stockService) {
    this.repository = repository;
    this.stockService = stockService;
  }

  @Transactional
  public Result importFrom(InputStream in) throws Exception {
    try (Workbook workbook = WorkbookFactory.create(in)) {
      Sheet sheet = workbook.getSheetAt(0);
      Row header = sheet.getRow(sheet.getFirstRowNum());
      if (header == null) {
        throw new IllegalArgumentException("Excel boş görünüyor (başlık satırı yok).");
      }

      Map<String, Integer> col = headerIndex(header);
      int codeCol = require(col, "kodu");
      int nameCol = require(col, "aciklamasi");
      Integer unitCol = col.get("ana birim");
      Integer supplierCol = col.get("aciklamasi 2");
      Integer stockCol = col.get("sevkedilebilir stok");

      // Load existing products once (by code) instead of a query per row.
      Map<String, Product> existing = new HashMap<>();
      for (Product p : repository.findAll()) {
        existing.put(p.getCode(), p);
      }

      List<Product> toSave = new java.util.ArrayList<>();
      Map<Product, Integer> targetStock = new java.util.IdentityHashMap<>();
      int imported = 0;
      int updated = 0;
      int skipped = 0;

      for (int r = header.getRowNum() + 1; r <= sheet.getLastRowNum(); r++) {
        Row row = sheet.getRow(r);
        if (row == null) {
          continue;
        }
        String code = text(row, codeCol);
        String name = text(row, nameCol);

        // Skip Go Plus dummy invoice rows and anything without a real name.
        if (code.isBlank() || code.startsWith("00-") || name.isBlank()) {
          skipped++;
          continue;
        }

        Product product = existing.get(code);
        if (product == null) {
          product = new Product(code, name);
          imported++;
        } else {
          product.setName(name);
          updated++;
        }
        product.setUnit(text(row, unitCol));
        product.setSupplier(text(row, supplierCol));
        if (stockCol != null) {
          targetStock.put(product, intValue(row, stockCol));
        }
        toSave.add(product);
      }

      repository.saveAll(toSave);

      // Stok farkı hareket defterine "Go Plus Aktarımı" olarak işlenir; doğrudan yazılmaz.
      for (Map.Entry<Product, Integer> e : targetStock.entrySet()) {
        int diff = e.getValue() - e.getKey().getStock();
        if (diff != 0) {
          stockService.move(e.getKey(), MovementType.AKTARIM, diff, "Go Plus stok dökümü", null, null);
        }
      }
      return new Result(imported, updated, skipped);
    }
  }

  // ── helpers ────────────────────────────────────────────────────────

  private Map<String, Integer> headerIndex(Row header) {
    Map<String, Integer> map = new HashMap<>();
    for (int c = header.getFirstCellNum(); c < header.getLastCellNum(); c++) {
      String key = fold(text(header, c));
      if (!key.isBlank()) {
        map.putIfAbsent(key, c);
      }
    }
    return map;
  }

  private int require(Map<String, Integer> col, String key) {
    Integer idx = col.get(key);
    if (idx == null) {
      throw new IllegalArgumentException(
          "Beklenen sütun bulunamadı: '" + key + "'. Go Plus stok dökümü mü yüklediniz?");
    }
    return idx;
  }

  /** Fold Turkish letters to ASCII and lowercase, so header matching is locale-proof. */
  private static String fold(String s) {
    String k =
        s.trim()
            .replace('İ', 'i')
            .replace('ı', 'i')
            .replace('I', 'i')
            .replace('Ş', 's')
            .replace('ş', 's')
            .replace('Ğ', 'g')
            .replace('ğ', 'g')
            .replace('Ü', 'u')
            .replace('ü', 'u')
            .replace('Ö', 'o')
            .replace('ö', 'o')
            .replace('Ç', 'c')
            .replace('ç', 'c');
    return k.toLowerCase(Locale.ROOT);
  }

  private String text(Row row, Integer colIndex) {
    if (colIndex == null || colIndex < 0) {
      return "";
    }
    Cell cell = row.getCell(colIndex);
    return cell == null ? "" : formatter.formatCellValue(cell).trim();
  }

  private int intValue(Row row, Integer colIndex) {
    if (colIndex == null) {
      return 0;
    }
    Cell cell = row.getCell(colIndex);
    if (cell == null) {
      return 0;
    }
    try {
      return switch (cell.getCellType()) {
        case NUMERIC -> (int) Math.round(cell.getNumericCellValue());
        case STRING -> {
          String v = cell.getStringCellValue().trim().replace(".", "").replace(",", "");
          yield v.isBlank() ? 0 : Integer.parseInt(v);
        }
        default -> 0;
      };
    } catch (NumberFormatException e) {
      return 0;
    }
  }
}
