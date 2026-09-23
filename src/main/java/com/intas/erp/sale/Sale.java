package com.intas.erp.sale;

import com.intas.erp.cari.Customer;
import com.intas.erp.common.EnumStringConverter;
import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * A sale ("satış fişi/faturası"). Starts as a DRAFT the user builds line by line;
 * COMPLETED on finalize, at which point each line's quantity is deducted from
 * product stock. Modelled on Go Plus's Satış Faturası but stripped to what the
 * shop uses day to day.
 */
@Entity
@Table(name = "sales")
public class Sale {

  public enum Status {
    DRAFT,
    COMPLETED
  }

  @Converter
  public static class StatusConverter extends EnumStringConverter<Status> {
    public StatusConverter() {
      super(Status.class);
    }
  }

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Convert(converter = StatusConverter.class)
  @Column(nullable = false, length = 16)
  private Status status = Status.DRAFT;

  @Column(length = 160)
  private String customerName;

  /** İsteğe bağlı cari hesap bağı; seçilirse satış bu cariyi borçlandırır. */
  @ManyToOne(fetch = FetchType.EAGER)
  @JoinColumn(name = "customer_id")
  private Customer customer;

  private Instant completedAt;

  @OneToMany(mappedBy = "sale", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
  @OrderBy("id asc")
  private List<SaleItem> items = new ArrayList<>();

  public Long getId() {
    return id;
  }

  public Status getStatus() {
    return status;
  }

  public void setStatus(Status status) {
    this.status = status;
  }

  public String getCustomerName() {
    return customerName;
  }

  public void setCustomerName(String customerName) {
    this.customerName = customerName;
  }

  public Customer getCustomer() {
    return customer;
  }

  public void setCustomer(Customer customer) {
    this.customer = customer;
  }

  public Instant getCompletedAt() {
    return completedAt;
  }

  public void setCompletedAt(Instant completedAt) {
    this.completedAt = completedAt;
  }

  private static final DateTimeFormatter LABEL_FORMAT =
      DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

  /** Şablonda kolay gösterim için yerel saatle biçimlenmiş tamamlanma tarihi. */
  public String getCompletedAtLabel() {
    return completedAt == null
        ? "-"
        : LABEL_FORMAT.format(completedAt.atZone(ZoneId.systemDefault()));
  }

  /** Fiş üzerindeki cari/müşteri etiketi. */
  public String getCustomerLabel() {
    if (customer != null) {
      return (customer.getCode() != null ? customer.getCode() + " · " : "") + customer.getName();
    }
    return customerName != null ? customerName : "Muhtelif Müşteri";
  }

  public List<SaleItem> getItems() {
    return items;
  }

  public void addItem(SaleItem item) {
    item.setSale(this);
    items.add(item);
  }

  public void removeItem(SaleItem item) {
    items.remove(item);
    item.setSale(null);
  }

  /** Total in base units across all lines (e.g. total ADET). */
  public int getTotalBaseQuantity() {
    return items.stream().mapToInt(SaleItem::getBaseQuantity).sum();
  }

  /** KDV hariç ara toplam; fiyat girilmemiş satırlar sıfır sayılır. */
  public BigDecimal getTotal() {
    return items.stream()
        .map(SaleItem::getLineTotal)
        .filter(v -> v != null)
        .reduce(BigDecimal.ZERO, BigDecimal::add);
  }

  /** Toplam KDV tutarı. */
  public BigDecimal getTotalVat() {
    return items.stream()
        .map(SaleItem::getVatAmount)
        .filter(v -> v != null)
        .reduce(BigDecimal.ZERO, BigDecimal::add);
  }

  /** KDV dahil genel toplam. */
  public BigDecimal getTotalGross() {
    return getTotal().add(getTotalVat());
  }
}
