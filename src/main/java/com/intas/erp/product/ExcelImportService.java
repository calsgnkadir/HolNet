package com.intas.erp.product;

import java.io.InputStream;
import java.math.BigDecimal;
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

  // ── Toplu güncelleme: fiyat / barkod / koli ─────────────────────────

  /** Toplu güncelleme sonucu; flash ile sayfaya taşındığı için Serializable. */
  public record UpdateResult(
      int matched,
      int prices,
      int barcodes,
      int cartons,
      List<String> unknownCodes,
      List<String> problems)
      implements java.io.Serializable {}

  /** Şablon ve kabul edilen başlıklar — sıra şablondaki sütun sırasıdır. */
  static final String[] TEMPLATE_HEADERS = {
    "Kodu", "Barkod", "Koli Barkodu", "Koli İçi Adet", "Satış Fiyatı", "Alış Fiyatı", "KDV"
  };

  private static final int MAX_LISTED = 50;

  /**
   * Updates existing products from a sheet keyed by "Kodu", with any subset of: Barkod,
   * Koli Barkodu, Koli İçi Adet, Satış Fiyatı, Alış Fiyatı, KDV. Built so the file can come
   * from anywhere — a Go Plus list export, a report, or the dealer's SQL query.
   *
   * <p>Safety rules: a missing column or empty cell leaves that field untouched (a price-only
   * file never wipes barcodes); stock is never changed; unknown codes are reported, not
   * created; a barcode already owned by another product is skipped and reported. Prices are
   * KDV hariç.
   */
  @Transactional
  public UpdateResult updateFrom(InputStream in) throws Exception {
    try (Workbook workbook = WorkbookFactory.create(in)) {
      Sheet sheet = workbook.getSheetAt(0);
      Row header = sheet.getRow(sheet.getFirstRowNum());
      if (header == null) {
        throw new IllegalArgumentException("Excel boş görünüyor (başlık satırı yok).");
      }
      Map<String, Integer> col = headerIndex(header);
      Integer codeCol = firstOf(col, "kodu", "kod", "malzeme kodu");
      if (codeCol == null) {
        throw new IllegalArgumentException("'Kodu' sütunu bulunamadı. Şablonu indirip onunla deneyin.");
      }
      Integer barcodeCol = firstOf(col, "barkod", "barkodu", "adet barkodu");
      Integer cartonBarcodeCol = firstOf(col, "koli barkodu");
      Integer perCartonCol = firstOf(col, "koli ici adet", "koli adedi", "koli ici");
      Integer priceCol = firstOf(col, "satis fiyati", "fiyat", "satis fiyati (kdv haric)");
      Integer costCol = firstOf(col, "alis fiyati", "alis fiyati (kdv haric)");
      Integer vatCol = firstOf(col, "kdv", "kdv orani", "kdv %");

      Map<String, Product> byCode = new HashMap<>();
      Map<String, Long> barcodeOwner = new HashMap<>();
      for (Product p : repository.findAll()) {
        byCode.put(p.getCode(), p);
        if (p.getBarcode() != null) {
          barcodeOwner.put(p.getBarcode(), p.getId());
        }
        if (p.getCartonBarcode() != null) {
          barcodeOwner.put(p.getCartonBarcode(), p.getId());
        }
      }

      int matched = 0, prices = 0, barcodes = 0, cartons = 0;
      List<String> unknown = new java.util.ArrayList<>();
      List<String> problems = new java.util.ArrayList<>();
      int unknownTotal = 0;

      for (int r = header.getRowNum() + 1; r <= sheet.getLastRowNum(); r++) {
        Row row = sheet.getRow(r);
        if (row == null) {
          continue;
        }
        String code = exactText(row, codeCol);
        if (code.isBlank()) {
          continue;
        }
        Product p = byCode.get(code);
        if (p == null) {
          unknownTotal++;
          if (unknown.size() < MAX_LISTED) {
            unknown.add(code);
          }
          continue;
        }
        matched++;
        int line = r + 1; // Excel'deki satır numarası

        try {
          String barcode = exactText(row, barcodeCol);
          if (!barcode.isBlank() && assignBarcode(p, barcode, false, barcodeOwner, problems, line)) {
            barcodes++;
          }
          String cartonBarcode = exactText(row, cartonBarcodeCol);
          if (!cartonBarcode.isBlank()
              && assignBarcode(p, cartonBarcode, true, barcodeOwner, problems, line)) {
            barcodes++;
          }

          BigDecimal perCarton = decimalValue(row, perCartonCol);
          if (perCarton != null) {
            if (perCarton.signum() <= 0 || perCarton.stripTrailingZeros().scale() > 0) {
              problem(problems, line, code, "koli içi adet tam sayı olmalı: " + perCarton);
            } else {
              p.setUnitsPerCarton(perCarton.intValueExact());
              if (p.getCartonUnit() == null) {
                p.setCartonUnit("KOLİ");
              }
              cartons++;
            }
          }

          BigDecimal price = decimalValue(row, priceCol);
          if (price != null) {
            if (price.signum() < 0) {
              problem(problems, line, code, "satış fiyatı negatif");
            } else {
              p.setPrice(price.setScale(2, java.math.RoundingMode.HALF_UP));
              prices++;
            }
          }

          BigDecimal cost = decimalValue(row, costCol);
          if (cost != null && cost.signum() >= 0) {
            p.setPurchasePrice(cost.setScale(4, java.math.RoundingMode.HALF_UP));
          }

          BigDecimal vat = decimalValue(row, vatCol);
          if (vat != null) {
            if (vat.signum() < 0 || vat.compareTo(BigDecimal.valueOf(100)) > 0) {
              problem(problems, line, code, "KDV 0–100 arası olmalı: " + vat);
            } else {
              p.setVatRate(vat);
            }
          }
        } catch (NumberFormatException e) {
          problem(problems, line, code, e.getMessage() + " — satırın kalanı atlandı");
        }
      }

      if (unknownTotal > unknown.size()) {
        unknown.add("… ve " + (unknownTotal - unknown.size()) + " kod daha");
      }
      // Değişen ürünler yönetilen (managed) varlıklar; commit'te yazılır.
      return new UpdateResult(matched, prices, barcodes, cartons, unknown, problems);
    }
  }

  /** Boş şablon (+ örnek satır) — kullanıcı indirip doldurur ya da bayiye gönderir. */
  public byte[] template() throws java.io.IOException {
    try (org.apache.poi.xssf.usermodel.XSSFWorkbook wb = new org.apache.poi.xssf.usermodel.XSSFWorkbook();
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream()) {
      Sheet sheet = wb.createSheet("Urunler");

      org.apache.poi.ss.usermodel.CellStyle text = wb.createCellStyle();
      text.setDataFormat(wb.createDataFormat().getFormat("@"));
      org.apache.poi.ss.usermodel.CellStyle bold = wb.createCellStyle();
      org.apache.poi.ss.usermodel.Font font = wb.createFont();
      font.setBold(true);
      bold.setFont(font);

      Row head = sheet.createRow(0);
      for (int c = 0; c < TEMPLATE_HEADERS.length; c++) {
        Cell cell = head.createCell(c);
        cell.setCellValue(TEMPLATE_HEADERS[c]);
        cell.setCellStyle(bold);
      }
      // Kod ve barkod sütunları metin: Excel uzun barkodu 8,69E+12'ye çevirmesin.
      for (int c = 0; c <= 2; c++) {
        sheet.setDefaultColumnStyle(c, text);
      }

      Row example = sheet.createRow(1);
      example.createCell(0).setCellValue("ORNEK-001");
      example.createCell(1).setCellValue("8690000000017");
      example.createCell(2).setCellValue("8690000000024");
      example.createCell(3).setCellValue(60);
      example.createCell(4).setCellValue(12.5);
      example.createCell(5).setCellValue(9.75);
      example.createCell(6).setCellValue(20);
      for (int c = 0; c <= 2; c++) {
        example.getCell(c).setCellStyle(text);
      }

      for (int c = 0; c < TEMPLATE_HEADERS.length; c++) {
        sheet.setColumnWidth(c, 18 * 256);
      }
      wb.write(out);
      return out.toByteArray();
    }
  }

  private boolean assignBarcode(
      Product p,
      String value,
      boolean carton,
      Map<String, Long> owner,
      List<String> problems,
      int line) {
    Long current = owner.get(value);
    if (current != null && !current.equals(p.getId())) {
      problem(problems, line, p.getCode(), "barkod " + value + " başka bir üründe kayıtlı — atlandı");
      return false;
    }
    String old = carton ? p.getCartonBarcode() : p.getBarcode();
    if (value.equals(old)) {
      return false; // değişiklik yok
    }
    if (old != null && p.getId().equals(owner.get(old))) {
      owner.remove(old);
    }
    if (carton) {
      p.setCartonBarcode(value);
    } else {
      p.setBarcode(value);
    }
    owner.put(value, p.getId());
    return true;
  }

  private static void problem(List<String> problems, int line, String code, String message) {
    if (problems.size() < MAX_LISTED) {
      problems.add("Satır " + line + " (" + code + "): " + message);
    }
  }

  private static Integer firstOf(Map<String, Integer> col, String... keys) {
    for (String k : keys) {
      Integer idx = col.get(k);
      if (idx != null) {
        return idx;
      }
    }
    return null;
  }

  /**
   * Cell as exact text. Numeric cells are written out in full — DataFormatter would render
   * a 13-digit barcode as "8,69E+12" (Excel's General format), silently corrupting it.
   */
  private String exactText(Row row, Integer colIndex) {
    if (colIndex == null) {
      return "";
    }
    Cell cell = row.getCell(colIndex);
    if (cell == null) {
      return "";
    }
    org.apache.poi.ss.usermodel.CellType type =
        cell.getCellType() == org.apache.poi.ss.usermodel.CellType.FORMULA
            ? cell.getCachedFormulaResultType()
            : cell.getCellType();
    return switch (type) {
      case NUMERIC -> BigDecimal.valueOf(cell.getNumericCellValue()).stripTrailingZeros().toPlainString();
      case STRING -> cell.getStringCellValue().trim();
      default -> formatter.formatCellValue(cell).trim();
    };
  }

  /**
   * Cell as a number, or null when blank. Numeric cells are read as-is. Text cells: with a
   * comma it is TR format ("1.234,50"); without one, a single dot is a decimal point
   * ("12.50", as SQL/CSV exports write it) and several dots are thousands separators.
   */
  private BigDecimal decimalValue(Row row, Integer colIndex) {
    if (colIndex == null) {
      return null;
    }
    Cell cell = row.getCell(colIndex);
    if (cell == null) {
      return null;
    }
    org.apache.poi.ss.usermodel.CellType type =
        cell.getCellType() == org.apache.poi.ss.usermodel.CellType.FORMULA
            ? cell.getCachedFormulaResultType()
            : cell.getCellType();
    if (type == org.apache.poi.ss.usermodel.CellType.NUMERIC) {
      return BigDecimal.valueOf(cell.getNumericCellValue());
    }
    String s = exactText(row, colIndex).replace(" ", "").replace("₺", "").replace("%", "");
    if (s.isBlank()) {
      return null;
    }
    try {
      if (s.contains(",")) {
        return com.intas.erp.common.Money.parse(s);
      }
      long dots = s.chars().filter(ch -> ch == '.').count();
      return new BigDecimal(dots > 1 ? s.replace(".", "") : s);
    } catch (NumberFormatException e) {
      // BigDecimal'ın İngilizce teknik mesajı yerine hücredeki değeri göster.
      throw new NumberFormatException("'" + s + "' sayı değil");
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
