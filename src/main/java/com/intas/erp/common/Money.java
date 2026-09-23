package com.intas.erp.common;

import java.math.BigDecimal;

/** Parses amounts the way people type them in Turkey. */
public final class Money {

  private Money() {}

  /**
   * "1.234,50" → 1234.50, "12,5" → 12.5; blank → null. Dots are thousands separators
   * and the comma is the decimal mark (TR format).
   *
   * @throws NumberFormatException when the text is not a number
   */
  public static BigDecimal parse(String raw) {
    if (raw == null || raw.isBlank()) {
      return null;
    }
    return new BigDecimal(raw.trim().replace(".", "").replace(',', '.'));
  }
}
