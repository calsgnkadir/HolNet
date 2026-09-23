package com.intas.erp.cari;

import com.intas.erp.common.EnumStringConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Converter;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * One movement on a customer account ("cari hesap fişi"). Exactly one of
 * {@code debit}/{@code credit} is non-zero: a sale posts a debit (borç, balance
 * up), a payment posts a credit (alacak, balance down). {@code balanceAfter}
 * snapshots the running balance so the ledger reads like a statement.
 */
@Entity
@Table(name = "cari_transactions")
public class CariTransaction {

  public enum Type {
    ACILIS, // opening balance / devir
    SATIS, // sale invoice → borç
    TAHSILAT, // payment received → alacak
    ALIS, // purchase invoice from a supplier → alacak (we owe them)
    DEKONT // manual adjustment
  }

  @Converter
  public static class TypeConverter extends EnumStringConverter<Type> {
    public TypeConverter() {
      super(Type.class);
    }
  }

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(optional = false, fetch = FetchType.EAGER)
  @JoinColumn(name = "customer_id")
  private Customer customer;

  @Convert(converter = TypeConverter.class)
  @Column(nullable = false, length = 16)
  private Type type;

  @Column(nullable = false, precision = 14, scale = 2)
  private BigDecimal debit = BigDecimal.ZERO;

  @Column(nullable = false, precision = 14, scale = 2)
  private BigDecimal credit = BigDecimal.ZERO;

  @Column(precision = 14, scale = 2)
  private BigDecimal balanceAfter;

  @Column(length = 200)
  private String description;

  /** Bağlı satış (varsa), rapor/izleme için. */
  private Long saleId;

  /** Bağlı alış faturası (varsa). */
  private Long purchaseId;

  private Instant createdAt = Instant.now();

  protected CariTransaction() {
    // JPA
  }

  public CariTransaction(Customer customer, Type type, BigDecimal debit, BigDecimal credit) {
    this.customer = customer;
    this.type = type;
    this.debit = debit == null ? BigDecimal.ZERO : debit;
    this.credit = credit == null ? BigDecimal.ZERO : credit;
  }

  public Long getId() {
    return id;
  }

  public Customer getCustomer() {
    return customer;
  }

  public Type getType() {
    return type;
  }

  public BigDecimal getDebit() {
    return debit;
  }

  public BigDecimal getCredit() {
    return credit;
  }

  public BigDecimal getBalanceAfter() {
    return balanceAfter;
  }

  public void setBalanceAfter(BigDecimal balanceAfter) {
    this.balanceAfter = balanceAfter;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public Long getSaleId() {
    return saleId;
  }

  public void setSaleId(Long saleId) {
    this.saleId = saleId;
  }

  public Long getPurchaseId() {
    return purchaseId;
  }

  public void setPurchaseId(Long purchaseId) {
    this.purchaseId = purchaseId;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }

  private static final DateTimeFormatter LABEL_FORMAT =
      DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

  /** Şablonda kolay gösterim için yerel saatle biçimlenmiş tarih. */
  public String getCreatedAtLabel() {
    return createdAt == null
        ? ""
        : LABEL_FORMAT.format(createdAt.atZone(ZoneId.systemDefault()));
  }

  /** Türkçe tür etiketi. */
  public String getTypeLabel() {
    return switch (type) {
      case ACILIS -> "Açılış";
      case SATIS -> "Satış";
      case TAHSILAT -> "Tahsilat";
      case ALIS -> "Alış";
      case DEKONT -> "Dekont";
    };
  }
}
