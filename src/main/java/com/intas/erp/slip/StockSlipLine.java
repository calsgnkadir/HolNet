package com.intas.erp.slip;

import com.intas.erp.product.Product;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** One line on a malzeme fişi; code/name are snapshotted like sale lines. */
@Entity
@Table(name = "stock_slip_lines")
public class StockSlipLine {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(optional = false)
  @JoinColumn(name = "slip_id")
  private StockSlip slip;

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

  /** ADET karşılığı — stoğa işlenen miktar (işaretsiz; yönü fiş türü belirler). */
  @Column(nullable = false)
  private int baseQuantity;

  protected StockSlipLine() {
    // JPA
  }

  public StockSlipLine(Product product, String unitLabel, int quantity, int baseQuantity) {
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

  public StockSlip getSlip() {
    return slip;
  }

  void setSlip(StockSlip slip) {
    this.slip = slip;
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
}
