package com.intas.erp.sale;

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
 * One line on a sale. Code/name are snapshotted so the record stays readable even
 * if the product later changes. {@code quantity} is what the user typed in the
 * chosen unit; {@code baseQuantity} is that converted to base units (ADET) and is
 * what gets deducted from stock — e.g. 3 KOLİ × 60 = 180 ADET.
 */
@Entity
@Table(name = "sale_items")
public class SaleItem {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(optional = false)
  @JoinColumn(name = "sale_id")
  private Sale sale;

  @ManyToOne(optional = false)
  @JoinColumn(name = "product_id")
  private Product product;

  @Column(nullable = false, length = 64)
  private String productCode;

  @Column(nullable = false, length = 300)
  private String productName;

  /** The unit the quantity was entered in — e.g. "ADET" or "KOLİ". */
  @Column(nullable = false, length = 32)
  private String unitLabel;

  @Column(nullable = false)
  private int quantity;

  @Column(nullable = false)
  private int baseQuantity;

  private BigDecimal unitPrice;

  /** KDV hariç satır tutarı = baseQuantity × unitPrice. */
  private BigDecimal lineTotal;

  /** Bu satıra uygulanan KDV oranı (%), üründen kopyalanır. */
  private BigDecimal vatRate;

  protected SaleItem() {
    // JPA
  }

  public SaleItem(Product product, String unitLabel, int quantity, int baseQuantity) {
    this.product = product;
    this.productCode = product.getCode();
    this.productName = product.getName();
    this.unitLabel = unitLabel;
    this.quantity = quantity;
    this.baseQuantity = baseQuantity;
  }

  public Long getId() {
    return id;
  }

  public Sale getSale() {
    return sale;
  }

  public void setSale(Sale sale) {
    this.sale = sale;
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

  public void setUnitPrice(BigDecimal unitPrice) {
    this.unitPrice = unitPrice;
  }

  public BigDecimal getLineTotal() {
    return lineTotal;
  }

  public void setLineTotal(BigDecimal lineTotal) {
    this.lineTotal = lineTotal;
  }

  public BigDecimal getVatRate() {
    return vatRate;
  }

  public void setVatRate(BigDecimal vatRate) {
    this.vatRate = vatRate;
  }

  /** KDV tutarı = lineTotal × vatRate/100, 2 haneye yuvarlanır. */
  public BigDecimal getVatAmount() {
    if (lineTotal == null || vatRate == null) {
      return null;
    }
    return lineTotal
        .multiply(vatRate)
        .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
  }

  /** KDV dahil satır tutarı. */
  public BigDecimal getLineGross() {
    if (lineTotal == null) {
      return null;
    }
    BigDecimal vat = getVatAmount();
    return vat == null ? lineTotal : lineTotal.add(vat);
  }
}
