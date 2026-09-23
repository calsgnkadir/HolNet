package com.intas.erp.product;

import java.math.BigDecimal;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class ProductController {

  private static final int PAGE_SIZE = 25;

  private final ProductRepository repository;
  private final ExcelImportService importService;
  private final StockService stockService;

  public ProductController(
      ProductRepository repository, ExcelImportService importService, StockService stockService) {
    this.repository = repository;
    this.importService = importService;
    this.stockService = stockService;
  }

  @GetMapping("/")
  public String home() {
    return "redirect:/urunler";
  }

  @GetMapping("/urunler")
  public String list(
      @RequestParam(name = "q", required = false) String q,
      @RequestParam(name = "page", defaultValue = "0") int page,
      Model model) {

    Pageable pageable = PageRequest.of(Math.max(page, 0), PAGE_SIZE, Sort.by("name").ascending());
    String query = q == null ? "" : q.trim();

    Page<Product> products =
        query.isBlank() ? repository.findAll(pageable) : repository.search(query, pageable);

    model.addAttribute("products", products);
    model.addAttribute("q", query);
    model.addAttribute("total", repository.count());
    return "products";
  }

  @GetMapping("/urunler/{id}/duzenle")
  public String editForm(@PathVariable Long id, Model model, RedirectAttributes redirect) {
    return repository
        .findById(id)
        .map(
            product -> {
              model.addAttribute("product", product);
              return "product-edit";
            })
        .orElseGet(
            () -> {
              redirect.addFlashAttribute("error", "Ürün bulunamadı.");
              return "redirect:/urunler";
            });
  }

  @GetMapping("/urunler/{id}/hareketler")
  public String movements(@PathVariable Long id, Model model, RedirectAttributes redirect) {
    Product product = repository.findById(id).orElse(null);
    if (product == null) {
      redirect.addFlashAttribute("error", "Ürün bulunamadı.");
      return "redirect:/urunler";
    }
    model.addAttribute("product", product);
    model.addAttribute("movements", stockService.history(product));
    return "product-movements";
  }

  @PostMapping("/urunler/{id}/duzenle")
  public String edit(
      @PathVariable Long id,
      @RequestParam(name = "name", required = false) String name,
      @RequestParam(name = "barcode", required = false) String barcode,
      @RequestParam(name = "supplier", required = false) String supplier,
      @RequestParam(name = "stock", required = false) Integer stock,
      @RequestParam(name = "price", required = false) String price,
      @RequestParam(name = "vatRate", required = false) String vatRate,
      @RequestParam(name = "cartonUnit", required = false) String cartonUnit,
      @RequestParam(name = "unitsPerCarton", required = false) String unitsPerCarton,
      RedirectAttributes redirect) {

    Product product = repository.findById(id).orElse(null);
    if (product == null) {
      redirect.addFlashAttribute("error", "Ürün bulunamadı.");
      return "redirect:/urunler";
    }

    try {
      if (name != null && !name.isBlank()) {
        product.setName(name.trim());
      }
      product.setBarcode(blankToNull(barcode));
      product.setSupplier(blankToNull(supplier));
      product.setPrice(parsePrice(price));
      BigDecimal vat = parsePrice(vatRate);
      product.setVatRate(vat != null ? vat : new BigDecimal("20"));
      product.setCartonUnit(blankToNull(cartonUnit));
      product.setUnitsPerCarton(parseUnitsPerCarton(unitsPerCarton));
      repository.save(product);
      // Stok alanı doğrudan yazılmaz: fark sayım fazlası/eksiği hareketi olarak işlenir.
      if (stock != null) {
        stockService.adjustTo(product.getId(), stock, "Ürün kartından sayım düzeltmesi");
      }
      redirect.addFlashAttribute("message", product.getCode() + " güncellendi.");
      return "redirect:/urunler?q=" + product.getCode();
    } catch (NumberFormatException e) {
      redirect.addFlashAttribute("error", "Fiyat veya koli adedi sayı olmalı.");
      return "redirect:/urunler/" + id + "/duzenle";
    }
  }

  private static String blankToNull(String value) {
    if (value == null) {
      return null;
    }
    String trimmed = value.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }

  /** Accepts "12,50" or "12.50"; blank means no price yet. */
  private static BigDecimal parsePrice(String raw) {
    return com.intas.erp.common.Money.parse(raw);
  }

  private static Integer parseUnitsPerCarton(String raw) {
    String value = blankToNull(raw);
    if (value == null) {
      return null;
    }
    int units = Integer.parseInt(value);
    return units > 0 ? units : null;
  }

  @PostMapping("/urunler/ice-aktar")
  public String importExcel(
      @RequestParam("file") MultipartFile file, RedirectAttributes redirect) {

    if (file == null || file.isEmpty()) {
      redirect.addFlashAttribute("error", "Lütfen bir Excel dosyası seçin.");
      return "redirect:/urunler";
    }
    try {
      ExcelImportService.Result result = importService.importFrom(file.getInputStream());
      redirect.addFlashAttribute(
          "message",
          "İçe aktarma tamamlandı — %d yeni, %d güncellendi, %d atlandı."
              .formatted(result.imported(), result.updated(), result.skipped()));
    } catch (Exception e) {
      redirect.addFlashAttribute("error", "İçe aktarma başarısız: " + e.getMessage());
    }
    return "redirect:/urunler";
  }
}
