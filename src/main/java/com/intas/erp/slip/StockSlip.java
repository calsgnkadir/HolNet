package com.intas.erp.slip;

import com.intas.erp.common.EnumStringConverter;
import com.intas.erp.product.MovementType;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Converter;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * A stock document ("malzeme fişi"), modelled on Go Plus's Malzeme Fişleri as the shop
 * uses them: (50) Sayım Fazlası for stock coming in and (51) Sayım Eksiği for stock going
 * out, with an optional belge no ("STOK GİRİŞ"). Built as a DRAFT line by line; saving
 * posts one stock movement per line.
 */
@Entity
@Table(name = "stock_slips")
public class StockSlip {

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

  /** SAYIM_FAZLASI (giriş) veya SAYIM_EKSIGI (çıkış); kaydedilirken seçilir. */
  @Convert(converter = MovementType.DbConverter.class)
  @Column(length = 20)
  private MovementType type;

  @Column(length = 64)
  private String documentNo;

  @Column(length = 200)
  private String description;

  private Instant completedAt;

  @OneToMany(mappedBy = "slip", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
  @OrderBy("id asc")
  private List<StockSlipLine> lines = new ArrayList<>();

  public boolean isIncoming() {
    return type == MovementType.SAYIM_FAZLASI;
  }

  public int getTotalBaseQuantity() {
    return lines.stream().mapToInt(StockSlipLine::getBaseQuantity).sum();
  }

  public String getCompletedAtLabel() {
    return completedAt == null ? "-" : LABEL_FORMAT.format(completedAt.atZone(ZoneId.systemDefault()));
  }

  public void addLine(StockSlipLine line) {
    line.setSlip(this);
    lines.add(line);
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

  public MovementType getType() {
    return type;
  }

  public void setType(MovementType type) {
    this.type = type;
  }

  public String getDocumentNo() {
    return documentNo;
  }

  public void setDocumentNo(String documentNo) {
    this.documentNo = documentNo;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public Instant getCompletedAt() {
    return completedAt;
  }

  public void setCompletedAt(Instant completedAt) {
    this.completedAt = completedAt;
  }

  public List<StockSlipLine> getLines() {
    return lines;
  }
}
