package com.intas.erp.purchase;

import com.intas.erp.cari.CariService;
import com.intas.erp.cari.Customer;
import com.intas.erp.cari.CustomerRepository;
import com.intas.erp.product.MovementType;
import com.intas.erp.product.Product;
import com.intas.erp.product.ProductRepository;
import com.intas.erp.product.StockService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PurchaseService {

  private static final BigDecimal DEFAULT_VAT = new BigDecimal("20");

  private final PurchaseRepository purchaseRepository;
  private final ProductRepository productRepository;
  private final CustomerRepository customerRepository;
  private final StockService stockService;
  private final CariService cariService;

  public PurchaseService(
      PurchaseRepository purchaseRepository,
      ProductRepository productRepository,
      CustomerRepository customerRepository,
      StockService stockService,
      CariService cariService) {
    this.purchaseRepository = purchaseRepository;
    this.productRepository = productRepository;
    this.customerRepository = customerRepository;
    this.stockService = stockService;
    this.cariService = cariService;
  }

  @Transactional
  public Purchase currentDraft() {
    return purchaseRepository
        .findFirstByStatusOrderByIdAsc(Purchase.Status.DRAFT)
        .orElseGet(() -> purchaseRepository.save(new Purchase()));
  }

  /**
   * Adds a line. When no price is typed, the product's last purchase cost is suggested
   * (scaled to a koli when buying by the koli), so repeat orders need no retyping.
   */
  @Transactional
  public void addLine(String code, int quantity, boolean byCarton, BigDecimal unitPrice) {
    if (quantity <= 0) {
      throw new IllegalArgumentException("Miktar 0'dan büyük olmalı.");
    }
    if (unitPrice != null && unitPrice.signum() < 0) {
      throw new IllegalArgumentException("Fiyat negatif olamaz.");
    }
    Product product = productRepository.requireByCodeOrBarcode(code);
    int baseQuantity = product.toBaseQuantity(quantity, byCarton);

    BigDecimal price = unitPrice;
    if (price == null && product.getPurchasePrice() != null) {
      int perUnit = product.toBaseQuantity(1, byCarton);
      price = product.getPurchasePrice().multiply(BigDecimal.valueOf(perUnit));
    }

    Purchase draft = currentDraft();
    draft.addLine(
        new PurchaseLine(
            product,
            product.unitLabelFor(byCarton),
            quantity,
            baseQuantity,
            price,
            product.getVatRate() != null ? product.getVatRate() : DEFAULT_VAT));
    purchaseRepository.save(draft);
  }

  @Transactional
  public void removeLine(Long lineId) {
    Purchase draft = currentDraft();
    draft.getLines().removeIf(l -> l.getId().equals(lineId));
    purchaseRepository.save(draft);
  }

  @Transactional
  public void clearDraft() {
    Purchase draft = currentDraft();
    draft.getLines().clear();
    purchaseRepository.save(draft);
  }

  /**
   * Completes the invoice in one transaction: stock in (ALIS movements), latest cost on
   * each product, and the supplier's cari credited with the KDV-inclusive total.
   */
  @Transactional
  public Purchase complete(Long supplierId, String documentNo) {
    Purchase draft = currentDraft();
    if (draft.getLines().isEmpty()) {
      throw new IllegalStateException("Boş fatura kaydedilemez — önce ürün ekleyin.");
    }
    if (supplierId == null) {
      throw new IllegalArgumentException("Tedarikçi (cari) seçin.");
    }
    Customer supplier =
        customerRepository
            .findById(supplierId)
            .orElseThrow(() -> new IllegalArgumentException("Cari bulunamadı: " + supplierId));

    draft.setSupplier(supplier);
    draft.setDocumentNo(documentNo == null || documentNo.isBlank() ? null : documentNo.trim());
    draft.setStatus(Purchase.Status.COMPLETED);
    draft.setCompletedAt(Instant.now());
    Purchase saved = purchaseRepository.save(draft);

    String note =
        "Alış Faturası #" + saved.getId()
            + (saved.getDocumentNo() != null ? " (" + saved.getDocumentNo() + ")" : "")
            + " — " + supplier.getName();
    for (PurchaseLine line : saved.getLines()) {
      stockService.move(
          line.getProduct(), MovementType.ALIS, line.getBaseQuantity(), note, "ALIS", saved.getId());
      if (line.getBaseUnitCost() != null) {
        line.getProduct().setPurchasePrice(line.getBaseUnitCost());
      }
    }

    cariService.postPurchase(supplier, saved.getId(), saved.getTotalGross(), note);
    return saved;
  }

  @Transactional(readOnly = true)
  public List<Purchase> completed() {
    return purchaseRepository.findByStatusOrderByIdDesc(Purchase.Status.COMPLETED);
  }

  @Transactional(readOnly = true)
  public Purchase get(Long id) {
    return purchaseRepository
        .findById(id)
        .orElseThrow(() -> new IllegalArgumentException("Alış faturası bulunamadı: " + id));
  }
}
