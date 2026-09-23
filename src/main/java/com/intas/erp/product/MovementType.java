package com.intas.erp.product;

import com.intas.erp.common.EnumStringConverter;
import jakarta.persistence.Converter;

/**
 * Stok hareket türleri. Go Plus'taki Malzeme Fişleri'nde dükkanın fiilen
 * kullandığı türler (50 Sayım Fazlası = giriş, 51 Sayım Eksiği = çıkış) +
 * satış/alış faturalarının otomatik ürettiği hareketler.
 */
public enum MovementType {
  AKTARIM("Go Plus Aktarımı"),
  SATIS("Satış"),
  ALIS("Alış Faturası"),
  SAYIM_FAZLASI("(50) Sayım Fazlası"),
  SAYIM_EKSIGI("(51) Sayım Eksiği");

  private final String label;

  MovementType(String label) {
    this.label = label;
  }

  public String getLabel() {
    return label;
  }

  @Converter
  public static class DbConverter extends EnumStringConverter<MovementType> {
    public DbConverter() {
      super(MovementType.class);
    }
  }
}
