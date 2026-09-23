package com.intas.erp.product;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * One change to a product's stock — the ledger behind {@link Product#getStock()}.
 * {@code quantity} is signed in base units (+ giriş, − çıkış) and {@code balanceAfter}
 * snapshots the resulting stock, so a product's history reads like a statement and the
 * current stock can always be explained. {@code refType}/{@code refId} point back to
 * the document that caused it (SATIS #12, FIS #3, ALIS #5).
 */
@Entity
@Table(
    name = "stock_movements",
    indexes = {@Index(name = "idx_movement_product", columnList = "product_id")})
public class StockMovement {

  private static final DateTimeFormatter LABEL_FORMAT =
      DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(optional = false, fetch = FetchType.EAGER)
  @JoinColumn(name = "product_id")
  private Product product;

  @Convert(converter = MovementType.DbConverter.class)
  @Column(nullable = false, length = 20)
  private MovementType type;

  /** İşaretli miktar, ADET cinsinden: + giriş, − çıkış. */
  @Column(nullable = false)
  private int quantity;

  @Column(nullable = false)
  private int balanceAfter;

  @Column(length = 200)
  private String description;

  @Column(length = 16)
  private String refType;

  private Long refId;

  private Instant createdAt = Instant.now();

  protected StockMovement() {
    // JPA
  }

  public StockMovement(Product product, MovementType type, int quantity, int balanceAfter) {
    this.product = product;
    this.type = type;
    this.quantity = quantity;
    this.balanceAfter = balanceAfter;
  }

  public boolean isIn() {
    return quantity > 0;
  }

  public String getCreatedAtLabel() {
    return createdAt == null ? "" : LABEL_FORMAT.format(createdAt.atZone(ZoneId.systemDefault()));
  }

  /** Kaynağın sayfası (varsa) — ekstrede tıklanabilir bağlantı için. */
  public String getRefLink() {
    if (refType == null || refId == null) {
      return null;
    }
    return switch (refType) {
      case "SATIS" -> "/satis/" + refId + "/fis";
      case "FIS" -> "/fisler/" + refId;
      case "ALIS" -> "/alis/" + refId;
      default -> null;
    };
  }

  public Long getId() {
    return id;
  }

  public Product getProduct() {
    return product;
  }

  public MovementType getType() {
    return type;
  }

  public int getQuantity() {
    return quantity;
  }

  public int getBalanceAfter() {
    return balanceAfter;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public String getRefType() {
    return refType;
  }

  public Long getRefId() {
    return refId;
  }

  public void setRef(String refType, Long refId) {
    this.refType = refType;
    this.refId = refId;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
