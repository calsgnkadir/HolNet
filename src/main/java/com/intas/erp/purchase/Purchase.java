package com.intas.erp.purchase;

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
import java.util.Objects;

/**
 * A purchase invoice from a supplier ("satınalma faturası"). Built as a DRAFT; completing
 * it brings the goods into stock, records each product's latest cost, and credits the
 * supplier's cari account with the KDV-inclusive total.
 */
@Entity
@Table(name = "purchases")
public class Purchase {

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

  private static final DateTimeFormatter LABEL_FORMAT =
      DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Convert(converter = StatusConverter.class)
  @Column(nullable = false, length = 16)
  private Status status = Status.DRAFT;

  @ManyToOne(fetch = FetchType.EAGER)
  @JoinColumn(name = "supplier_id")
  private Customer supplier;

  /** Tedarikçinin fatura numarası (kağıt/e-fatura üzerindeki). */
  @Column(length = 64)
  private String documentNo;

  private Instant completedAt;

  @OneToMany(mappedBy = "purchase", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
  @OrderBy("id asc")
  private List<PurchaseLine> lines = new ArrayList<>();

  public void addLine(PurchaseLine line) {
    line.setPurchase(this);
    lines.add(line);
  }

  public int getTotalBaseQuantity() {
    return lines.stream().mapToInt(PurchaseLine::getBaseQuantity).sum();
  }

  /** KDV hariç ara toplam. */
  public BigDecimal getTotal() {
    return lines.stream()
        .map(PurchaseLine::getLineTotal)
        .filter(Objects::nonNull)
        .reduce(BigDecimal.ZERO, BigDecimal::add);
  }

  public BigDecimal getTotalVat() {
    return lines.stream()
        .map(PurchaseLine::getVatAmount)
        .filter(Objects::nonNull)
        .reduce(BigDecimal.ZERO, BigDecimal::add);
  }

  public BigDecimal getTotalGross() {
    return getTotal().add(getTotalVat());
  }

  public String getSupplierLabel() {
    if (supplier == null) {
      return "-";
    }
    return (supplier.getCode() != null ? supplier.getCode() + " · " : "") + supplier.getName();
  }

  public String getCompletedAtLabel() {
    return completedAt == null ? "-" : LABEL_FORMAT.format(completedAt.atZone(ZoneId.systemDefault()));
  }

  public Long getId() {
    return id;
  }

  public Status getStatus() {
    return status;
  }

  public void setStatus(Status status) {
    this.status = status;
  }

  public Customer getSupplier() {
    return supplier;
  }

  public void setSupplier(Customer supplier) {
    this.supplier = supplier;
  }

  public String getDocumentNo() {
    return documentNo;
  }

  public void setDocumentNo(String documentNo) {
    this.documentNo = documentNo;
  }

  public Instant getCompletedAt() {
    return completedAt;
  }

  public void setCompletedAt(Instant completedAt) {
    this.completedAt = completedAt;
  }

  public List<PurchaseLine> getLines() {
    return lines;
  }
}
