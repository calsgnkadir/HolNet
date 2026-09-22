package com.intas.erp.cari;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * A customer account ("cari hesap"), modelled on Go Plus's Cari Hesaplar. The
 * {@code balance} is the running account balance in the shop's convention:
 * <b>positive = borç</b> (the customer owes us), negative = alacak (we owe them /
 * they are in credit). A completed sale increases the balance; a payment
 * (tahsilat) decreases it. Movements are recorded as {@link CariTransaction}s.
 */
@Entity
@Table(
    name = "customers",
    indexes = {@Index(name = "idx_customer_code", columnList = "code", unique = true)})
public class Customer {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  /** Go Plus muhasebe kodu (örn. 120.01.057); boş bırakılırsa otomatik üretilir. */
  @Column(unique = true, length = 32)
  private String code;

  @Column(nullable = false, length = 160)
  private String name;

  @Column(length = 80)
  private String city;

  @Column(length = 40)
  private String phone;

  /** Cari bakiye — pozitif = borç (müşteri bize borçlu), negatif = alacak. */
  @Column(nullable = false, precision = 14, scale = 2)
  private BigDecimal balance = BigDecimal.ZERO;

  private Instant createdAt = Instant.now();

  protected Customer() {
    // JPA
  }

  public Customer(String code, String name) {
    this.code = code;
    this.name = name;
  }

  /** true = borç (kırmızı), false = alacak/sıfır. UI kolaylığı için. */
  public boolean isDebtor() {
    return balance != null && balance.signum() > 0;
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

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getCity() {
    return city;
  }

  public void setCity(String city) {
    this.city = city;
  }

  public String getPhone() {
    return phone;
  }

  public void setPhone(String phone) {
    this.phone = phone;
  }

  public BigDecimal getBalance() {
    return balance;
  }

  public void setBalance(BigDecimal balance) {
    this.balance = balance;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }
}
