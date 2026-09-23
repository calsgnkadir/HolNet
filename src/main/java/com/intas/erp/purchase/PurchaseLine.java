package com.intas.erp.purchase;

import com.intas.erp.product.Product;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * One line on a purchase invoice. {@code unitPrice} is the cost per <em>entered</em> unit
 * (per koli when bought by the koli, per adet otherwise), KDV hariç — that is how supplier
 * invoices are written. {@link #getBaseUnitCost()} converts it back to a per-adet cost.
 */
@Entity
@Table(name = "purchase_lines")
public class PurchaseLine {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(optional = false)
  @JoinColumn(name = "purchase_id")
  private Purchase purchase;

  @ManyToOne(optional = false)
  @JoinColumn(name = "product_id")
  private Product product;

  @Column(nullable = false, length = 64)
  private String productCode;

  @Column(nullable = false, length = 300)
  private String productName;

  @Column(nullable = false, length = 32)
  private String unitLabel;

  @Column(nullable = false)
  private int quantity;

  @Column(nullable = false)
  private int baseQuantity;

  @Column(precision = 12, scale = 4)
  private BigDecimal unitPrice;

  /** KDV hariç = quantity × unitPrice. */
  @Column(precision = 14, scale = 2)
  private BigDecimal lineTotal;

  @Column(precision = 5, scale = 2)
  private BigDecimal vatRate;

  protected PurchaseLine() {
    // JPA
  }

  public PurchaseLine(
      Product product,
      String unitLabel,
      int quantity,
      int baseQuantity,
      BigDecimal unitPrice,
      BigDecimal vatRate) {
    this.product = product;
    this.productCode = product.getCode();
    this.productName = product.getName();
    this.unitLabel = unitLabel;
    this.quantity = quantity;
    this.baseQuantity = baseQuantity;
    this.unitPrice = unitPrice;
    this.vatRate = vatRate;
    this.lineTotal =
        unitPrice == null
            ? null
            : unitPrice.multiply(BigDecimal.valueOf(quantity)).setScale(2, RoundingMode.HALF_UP);
  }

  public BigDecimal getVatAmount() {
    if (lineTotal == null || vatRate == null) {
      return null;
    }
    return lineTotal.multiply(vatRate).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
  }

  public BigDecimal getLineGross() {
    if (lineTotal == null) {
      return null;
    }
    BigDecimal vat = getVatAmount();
    return vat == null ? lineTotal : lineTotal.add(vat);
  }

  /** Adet başı maliyet (KDV hariç) — ürünün "son alış fiyatı" olarak kaydedilir. */
  public BigDecimal getBaseUnitCost() {
    if (lineTotal == null || baseQuantity <= 0) {
      return null;
    }
    return lineTotal.divide(BigDecimal.valueOf(baseQuantity), 4, RoundingMode.HALF_UP);
  }

  public Long getId() {
    return id;
  }

  void setPurchase(Purchase purchase) {
    this.purchase = purchase;
  }

  public Product getProduct() {
    return product;
  }

  public String getProductCode() {
    return productCode;
  }

  public String getProductName() {
    return productName;
  }

  public String getUnitLabel() {
    return unitLabel;
  }

  public int getQuantity() {
    return quantity;
  }

  public int getBaseQuantity() {
    return baseQuantity;
  }

  public BigDecimal getUnitPrice() {
    return unitPrice;
  }

  public BigDecimal getLineTotal() {
    return lineTotal;
  }

  public BigDecimal getVatRate() {
    return vatRate;
  }
}
