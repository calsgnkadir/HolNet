package com.intas.erp.cari;

import java.math.BigDecimal;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cari hesap işlemleri. Bakiye tek yerde, {@link #apply} üzerinden değişir:
 * borç bakiyeyi artırır (müşteri borçlanır), alacak azaltır (tahsilat). Her
 * hareket {@link CariTransaction} olarak, o anki bakiye {@code balanceAfter}
 * ile birlikte kaydedilir — böylece defter bir ekstre gibi okunur.
 */
@Service
public class CariService {

  private final CustomerRepository customerRepository;
  private final CariTransactionRepository transactionRepository;

  public CariService(
      CustomerRepository customerRepository, CariTransactionRepository transactionRepository) {
    this.customerRepository = customerRepository;
    this.transactionRepository = transactionRepository;
  }

  @Transactional(readOnly = true)
  public List<Customer> all() {
    return customerRepository.findAllByOrderByNameAsc();
  }

  @Transactional(readOnly = true)
  public Customer get(Long id) {
    return customerRepository
        .findById(id)
        .orElseThrow(() -> new IllegalArgumentException("Cari bulunamadı: " + id));
  }

  @Transactional(readOnly = true)
  public List<CariTransaction> ledger(Customer customer) {
    return transactionRepository.findByCustomerOrderByIdAsc(customer);
  }

  /** Toplam açık bakiye (tüm carilerin borç toplamı). */
  @Transactional(readOnly = true)
  public BigDecimal totalReceivable() {
    return customerRepository.findAll().stream()
        .map(Customer::getBalance)
        .filter(b -> b != null)
        .reduce(BigDecimal.ZERO, BigDecimal::add);
  }

  @Transactional
  public Customer create(
      String code, String name, String city, String phone, BigDecimal openingBalance) {
    if (name == null || name.isBlank()) {
      throw new IllegalArgumentException("Cari adı boş olamaz.");
    }
    String finalCode = (code == null || code.isBlank()) ? nextCode() : code.trim();
    if (customerRepository.findByCode(finalCode).isPresent()) {
      throw new IllegalArgumentException("Bu kod zaten kullanımda: " + finalCode);
    }
    Customer customer = new Customer(finalCode, name.trim());
    customer.setCity(blankToNull(city));
    customer.setPhone(blankToNull(phone));
    customer.setBalance(BigDecimal.ZERO);
    customer = customerRepository.save(customer);

    if (openingBalance != null && openingBalance.signum() != 0) {
      // Açılış borcu: pozitifse borç, negatifse alacak.
      BigDecimal debit = openingBalance.signum() > 0 ? openingBalance : BigDecimal.ZERO;
      BigDecimal credit = openingBalance.signum() < 0 ? openingBalance.negate() : BigDecimal.ZERO;
      apply(customer, CariTransaction.Type.ACILIS, debit, credit, "Açılış bakiyesi", null);
    }
    return customer;
  }

  /** Satış cariyi borçlandırır (KDV dahil tutar). */
  @Transactional
  public void postSale(Customer customer, Long saleId, BigDecimal grossAmount, String description) {
    if (customer == null || grossAmount == null || grossAmount.signum() == 0) {
      return;
    }
    apply(customer, CariTransaction.Type.SATIS, grossAmount, BigDecimal.ZERO, description, saleId);
  }

  /** Tahsilat cariyi alacaklandırır (bakiye düşer). */
  @Transactional
  public void recordPayment(Long customerId, BigDecimal amount, String note) {
    if (amount == null || amount.signum() <= 0) {
      throw new IllegalArgumentException("Tahsilat tutarı 0'dan büyük olmalı.");
    }
    Customer customer = get(customerId);
    apply(
        customer,
        CariTransaction.Type.TAHSILAT,
        BigDecimal.ZERO,
        amount,
        blankToNull(note) != null ? note.trim() : "Tahsilat",
        null);
  }

  /** Tek giriş noktası: bakiyeyi günceller ve hareketi defter kaydına yazar. */
  private void apply(
      Customer customer,
      CariTransaction.Type type,
      BigDecimal debit,
      BigDecimal credit,
      String description,
      Long saleId) {
    BigDecimal newBalance = customer.getBalance().add(debit).subtract(credit);
    customer.setBalance(newBalance);
    customerRepository.save(customer);

    CariTransaction tx = new CariTransaction(customer, type, debit, credit);
    tx.setDescription(description);
    tx.setSaleId(saleId);
    tx.setBalanceAfter(newBalance);
    transactionRepository.save(tx);
  }

  /** Sıradaki 120.01.NNN kodu; boşluk olsa da çakışmayı garanti eder. */
  private String nextCode() {
    int n = customerRepository.findAll().size() + 1;
    String code;
    do {
      code = "120.01.%03d".formatted(n++);
    } while (customerRepository.findByCode(code).isPresent());
    return code;
  }

  private static String blankToNull(String value) {
    if (value == null) {
      return null;
    }
    String trimmed = value.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }
}
