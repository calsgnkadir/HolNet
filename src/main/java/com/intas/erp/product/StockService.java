package com.intas.erp.product;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The only place product stock changes. Every change goes through {@link #move}, which
 * updates {@link Product#getStock()} and writes a {@link StockMovement} with the new
 * balance in the same transaction — so stock is never edited without a trace. Same
 * pattern as {@code CariService.apply} for account balances.
 */
@Service
public class StockService {

  private final ProductRepository productRepository;
  private final StockMovementRepository movementRepository;

  public StockService(
      ProductRepository productRepository, StockMovementRepository movementRepository) {
    this.productRepository = productRepository;
    this.movementRepository = movementRepository;
  }

  /**
   * Applies a signed base-unit quantity (+ giriş, − çıkış) to the product and records it.
   * Stock may go negative: when the recorded figure lags reality the movement still
   * reflects what physically happened, and a sayım fişi reconciles it later.
   */
  @Transactional
  public StockMovement move(
      Product product,
      MovementType type,
      int signedQuantity,
      String description,
      String refType,
      Long refId) {
    if (signedQuantity == 0) {
      throw new IllegalArgumentException("Stok hareketi 0 adet olamaz.");
    }
    // Re-read inside this transaction: callers may hold a detached copy (e.g. a
    // controller), and when they already run in a transaction this returns the same
    // managed instance, so nothing is loaded twice.
    Product managed =
        productRepository
            .findById(product.getId())
            .orElseThrow(() -> new IllegalArgumentException("Ürün bulunamadı: " + product.getCode()));

    int newStock = managed.getStock() + signedQuantity;
    managed.setStock(newStock);
    productRepository.save(managed);

    StockMovement movement = new StockMovement(managed, type, signedQuantity, newStock);
    movement.setDescription(description);
    movement.setRef(refType, refId);
    return movementRepository.save(movement);
  }

  /**
   * Sets stock to an absolute counted value by posting the difference as a sayım
   * fazlası/eksiği — used when someone corrects the figure on the product screen.
   * No-op when nothing changed.
   */
  @Transactional
  public void adjustTo(Long productId, int countedStock, String description) {
    Product product =
        productRepository
            .findById(productId)
            .orElseThrow(() -> new IllegalArgumentException("Ürün bulunamadı: " + productId));
    int diff = countedStock - product.getStock();
    if (diff == 0) {
      return;
    }
    MovementType type = diff > 0 ? MovementType.SAYIM_FAZLASI : MovementType.SAYIM_EKSIGI;
    move(product, type, diff, description, null, null);
  }

  @Transactional(readOnly = true)
  public java.util.List<StockMovement> history(Product product) {
    return movementRepository.findByProductOrderByIdDesc(product);
  }
}
