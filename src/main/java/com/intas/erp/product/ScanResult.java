package com.intas.erp.product;

/**
 * What a typed or scanned value resolved to: the product, and whether it was the
 * product's koli barcode — in which case the line should be taken as koli.
 */
public record ScanResult(Product product, boolean carton) {

  /** Koli seçildiyse ya da koli barkodu okutulduysa true. */
  public boolean asCarton(boolean cartonSelected) {
    return cartonSelected || carton;
  }
}
