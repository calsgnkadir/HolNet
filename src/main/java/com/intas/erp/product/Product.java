package com.intas.erp.product;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.math.BigDecimal;

/**
 * A stock item, modelled on the shop's Go Plus stock card. {@code code} is the
 * owner's product code (unique); {@code barcode} is optional and used by the
 * phone-camera scanner. Prices are not in the Go Plus export, so {@code price}
 * is nullable until a price list is imported or entered.
 */
@Entity
@Table(
    name = "products",
    indexes = {
      @Index(name = "idx_product_code", columnList = "code", unique = true),
      @Index(name = "idx_product_barcode", columnList = "barcode"),
      @Index(name = "idx_product_carton_barcode", columnList = "cartonBarcode")
    })
public class Product {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, unique = true, length = 64)
  private String code;

  @Column(length = 64)
  private String barcode;

  @Column(nullable = false, length = 300)
  private String name;

  /** Base unit stock is tracked in — Go Plus "Ana Birim" (usually ADET). */
  @Column(length = 32)
  private String unit;

  /**
   * Go Plus "Birim Seti": the carton unit (e.g. KOLİ) and how many base units it
   * holds ("1 KOLİ = 60 ADET" → cartonUnit=KOLİ, unitsPerCarton=60). Nullable when
   * a product is only sold loose. Selling N cartons deducts N*unitsPerCarton from
   * {@link #stock}. This is the heart of the "koli koli, altışar onikişer" flow.
   */
  @Column(length = 32)
  private String cartonUnit;

  private Integer unitsPerCarton;

  /**
   * Kolinin kendi barkodu (Go Plus "Birimli Barkod" — birim başına ayrı barkod).
   * Bu okutulunca satır otomatik koli olarak eklenir.
   */
  @Column(length = 64)
  private String cartonBarcode;

  @Column(length = 160)
  private String supplier;

  /** On-hand quantity in the base unit; decremented as sales are recorded. */
  @Column(nullable = false)
  private int stock;

  @Column(precision = 12, scale = 2)
  private BigDecimal price;

  /** KDV oranı (%). Çoğu ürün %20; dökümde yok, düzenleme ekranından girilir. */
  @Column(precision = 5, scale = 2)
  private BigDecimal vatRate = new BigDecimal("20");

  /** Son alış maliyeti (adet başı, KDV hariç) — satınalma faturası tamamlanınca güncellenir. */
  @Column(precision = 12, scale = 4)
  private BigDecimal purchasePrice;

  protected Product() {
    // JPA
  }

  /** Koli ile girilebiliyor mu (birim setinde çevrim katsayısı var mı). */
  public boolean hasCarton() {
    return unitsPerCarton != null && unitsPerCarton > 0;
  }

  /**
   * Girilen miktarı ana birime (ADET) çevirir: koli seçildiyse ve katsayı varsa
   * {@code miktar × koliİçiAdet}, değilse miktarın kendisi. Stok hep ADET'le tutulur.
   */
  public int toBaseQuantity(int quantity, boolean byCarton) {
    return byCarton && hasCarton() ? quantity * unitsPerCarton : quantity;
  }

  /** Satırda gösterilecek birim adı: KOLİ / ADET. */
  public String unitLabelFor(boolean byCarton) {
    if (byCarton && hasCarton()) {
      return cartonUnit != null ? cartonUnit : "KOLİ";
    }
    return unit != null ? unit : "ADET";
  }

  public Product(String code, String name) {
    this.code = code;
    this.name = name;
  }

  public Long getId() {
    return id;
  }

  public String getCode() {
    return code;
  }

  public void setCode(String code) {
    this.code = code;
  }

  public String getBarcode() {
    return barcode;
  }

  public void setBarcode(String barcode) {
    this.barcode = barcode;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getUnit() {
    return unit;
  }

  public void setUnit(String unit) {
    this.unit = unit;
  }

  public String getCartonUnit() {
    return cartonUnit;
  }

  public void setCartonUnit(String cartonUnit) {
    this.cartonUnit = cartonUnit;
  }

  public Integer getUnitsPerCarton() {
    return unitsPerCarton;
  }

  public String getCartonBarcode() {
    return cartonBarcode;
  }

  public void setCartonBarcode(String cartonBarcode) {
    this.cartonBarcode = cartonBarcode;
  }

  public void setUnitsPerCarton(Integer unitsPerCarton) {
    this.unitsPerCarton = unitsPerCarton;
  }

  public String getSupplier() {
    return supplier;
  }

  public void setSupplier(String supplier) {
    this.supplier = supplier;
  }

  public int getStock() {
    return stock;
  }

  public void setStock(int stock) {
    this.stock = stock;
  }

  public BigDecimal getPrice() {
    return price;
  }

  public void setPrice(BigDecimal price) {
    this.price = price;
  }

  public BigDecimal getVatRate() {
    return vatRate;
  }

  public void setVatRate(BigDecimal vatRate) {
    this.vatRate = vatRate;
  }

  public BigDecimal getPurchasePrice() {
    return purchasePrice;
  }

  public void setPurchasePrice(BigDecimal purchasePrice) {
    this.purchasePrice = purchasePrice;
  }
}
