package com.intas.erp.slip;

import com.intas.erp.product.MovementType;
import com.intas.erp.product.Product;
import com.intas.erp.product.ProductRepository;
import com.intas.erp.product.ScanResult;
import com.intas.erp.product.StockService;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StockSlipService {

  private final StockSlipRepository slipRepository;
  private final ProductRepository productRepository;
  private final StockService stockService;

  public StockSlipService(
      StockSlipRepository slipRepository,
      ProductRepository productRepository,
      StockService stockService) {
    this.slipRepository = slipRepository;
    this.productRepository = productRepository;
    this.stockService = stockService;
  }

  /** The single in-progress slip; created on first use. */
  @Transactional
  public StockSlip currentDraft() {
    return slipRepository
        .findFirstByStatusOrderByIdAsc(StockSlip.Status.DRAFT)
        .orElseGet(() -> slipRepository.save(new StockSlip()));
  }

  @Transactional
  public void addLine(String code, int quantity, boolean byCarton) {
    if (quantity <= 0) {
      throw new IllegalArgumentException("Miktar 0'dan büyük olmalı.");
    }
    ScanResult scan = productRepository.resolveScan(code);
    Product product = scan.product();
    boolean asCarton = scan.asCarton(byCarton);
    StockSlip draft = currentDraft();
    draft.addLine(
        new StockSlipLine(
            product,
            product.unitLabelFor(asCarton),
            quantity,
            product.toBaseQuantity(quantity, asCarton)));
    slipRepository.save(draft);
  }

  @Transactional
  public void removeLine(Long lineId) {
    StockSlip draft = currentDraft();
    draft.getLines().removeIf(l -> l.getId().equals(lineId));
    slipRepository.save(draft);
  }

  @Transactional
  public void clearDraft() {
    StockSlip draft = currentDraft();
    draft.getLines().clear();
    slipRepository.save(draft);
  }

  /**
   * Saves the draft as a (50) giriş or (51) çıkış fişi and posts one stock movement per
   * line — all in one transaction, so a fiş is either fully applied or not at all.
   */
  @Transactional
  public StockSlip complete(boolean incoming, String documentNo, String description) {
    StockSlip draft = currentDraft();
    if (draft.getLines().isEmpty()) {
      throw new IllegalStateException("Boş fiş kaydedilemez — önce ürün ekleyin.");
    }
    draft.setType(incoming ? MovementType.SAYIM_FAZLASI : MovementType.SAYIM_EKSIGI);
    draft.setDocumentNo(blankToNull(documentNo));
    draft.setDescription(blankToNull(description));
    draft.setStatus(StockSlip.Status.COMPLETED);
    draft.setCompletedAt(Instant.now());
    StockSlip saved = slipRepository.save(draft);

    String note =
        "Malzeme Fişi #" + saved.getId() + (saved.getDocumentNo() != null ? " — " + saved.getDocumentNo() : "");
    for (StockSlipLine line : saved.getLines()) {
      int signed = incoming ? line.getBaseQuantity() : -line.getBaseQuantity();
      stockService.move(line.getProduct(), saved.getType(), signed, note, "FIS", saved.getId());
    }
    return saved;
  }

  @Transactional(readOnly = true)
  public List<StockSlip> completed() {
    return slipRepository.findByStatusOrderByIdDesc(StockSlip.Status.COMPLETED);
  }

  @Transactional(readOnly = true)
  public StockSlip get(Long id) {
    return slipRepository
        .findById(id)
        .orElseThrow(() -> new IllegalArgumentException("Fiş bulunamadı: " + id));
  }

  private static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }
}
