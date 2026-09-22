package com.intas.erp.sale;

import com.intas.erp.cari.CariService;
import com.intas.erp.cari.Customer;
import com.intas.erp.cari.CustomerRepository;
import com.intas.erp.product.Product;
import com.intas.erp.product.ProductRepository;
import java.math.BigDecimal;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SaleService {

  private static final BigDecimal DEFAULT_VAT = new BigDecimal("20");

  private final SaleRepository saleRepository;
  private final ProductRepository productRepository;
  private final CustomerRepository customerRepository;
  private final CariService cariService;

  public SaleService(
      SaleRepository saleRepository,
      ProductRepository productRepository,
      CustomerRepository customerRepository,
      CariService cariService) {
    this.saleRepository = saleRepository;
    this.productRepository = productRepository;
    this.customerRepository = customerRepository;
    this.cariService = cariService;
  }

  /** The single in-progress sale; created on first use. */
  @Transactional
  public Sale currentDraft() {
    return saleRepository
        .findFirstByStatusOrderByIdAsc(Sale.Status.DRAFT)
        .orElseGet(() -> saleRepository.save(new Sale()));
  }

  /**
   * Adds a line to the current draft. {@code code} is matched against product code
   * first, then barcode (so a phone-camera scan works later). When {@code byCarton}
   * and the product has a carton factor, the quantity is multiplied out to base
   * units for stock — this is the koli→adet conversion.
   */
  @Transactional
  public void addLine(String code, int quantity, boolean byCarton) {
    if (code == null || code.isBlank()) {
      throw new IllegalArgumentException("Ürün kodu/barkod boş olamaz.");
    }
    if (quantity <= 0) {
      throw new IllegalArgumentException("Miktar 0'dan büyük olmalı.");
    }

    String key = code.trim();
    Product product =
        productRepository
            .findByCode(key)
            .or(() -> productRepository.findByBarcode(key))
            .orElseThrow(
                () -> new IllegalArgumentException("Ürün bulunamadı: " + key));

    boolean asCarton =
        byCarton && product.getUnitsPerCarton() != null && product.getUnitsPerCarton() > 0;
    int baseQuantity = asCarton ? quantity * product.getUnitsPerCarton() : quantity;
    String unitLabel =
        asCarton
            ? (product.getCartonUnit() != null ? product.getCartonUnit() : "KOLİ")
            : (product.getUnit() != null ? product.getUnit() : "ADET");

    SaleItem item = new SaleItem(product, unitLabel, quantity, baseQuantity);
    item.setVatRate(product.getVatRate() != null ? product.getVatRate() : DEFAULT_VAT);
    if (product.getPrice() != null) {
      item.setUnitPrice(product.getPrice());
      item.setLineTotal(product.getPrice().multiply(BigDecimal.valueOf(baseQuantity)));
    }

    Sale draft = currentDraft();
    draft.addItem(item);
    saleRepository.save(draft);
  }

  @Transactional
  public void removeLine(Long itemId) {
    Sale draft = currentDraft();
    draft.getItems().removeIf(i -> i.getId().equals(itemId));
    saleRepository.save(draft);
  }

  @Transactional
  public void clearDraft() {
    Sale draft = currentDraft();
    draft.getItems().clear();
    saleRepository.save(draft);
  }

  /**
   * Finalizes the draft: deducts each line's base quantity from stock and marks the
   * sale COMPLETED. Stock may go negative when the imported figure lags reality —
   * the sale still reflects what physically left, which the shop can reconcile.
   */
  @Transactional
  public Sale complete(Long customerId, String customerName) {
    Sale draft = currentDraft();
    if (draft.getItems().isEmpty()) {
      throw new IllegalStateException("Boş satış tamamlanamaz — önce ürün ekleyin.");
    }
    for (SaleItem item : draft.getItems()) {
      Product product = item.getProduct();
      product.setStock(product.getStock() - item.getBaseQuantity());
      productRepository.save(product);
    }

    Customer customer =
        customerId == null ? null : customerRepository.findById(customerId).orElse(null);
    if (customer != null) {
      draft.setCustomer(customer);
      draft.setCustomerName(customer.getName());
    } else {
      draft.setCustomerName(
          customerName == null || customerName.isBlank() ? null : customerName.trim());
    }

    draft.setStatus(Sale.Status.COMPLETED);
    draft.setCompletedAt(Instant.now());
    Sale saved = saleRepository.save(draft);

    // Cari seçildiyse KDV dahil tutarı borç olarak işle.
    if (customer != null) {
      cariService.postSale(
          customer, saved.getId(), saved.getTotalGross(), "Satış Fişi #" + saved.getId());
    }
    return saved;
  }

  @Transactional(readOnly = true)
  public java.util.List<Sale> completedSales() {
    return saleRepository.findByStatusOrderByIdDesc(Sale.Status.COMPLETED);
  }

  @Transactional(readOnly = true)
  public Sale get(Long id) {
    return saleRepository
        .findById(id)
        .orElseThrow(() -> new IllegalArgumentException("Satış bulunamadı: " + id));
  }
}
