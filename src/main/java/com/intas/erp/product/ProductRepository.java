package com.intas.erp.product;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductRepository extends JpaRepository<Product, Long> {

  Optional<Product> findByCode(String code);

  Optional<Product> findByBarcode(String barcode);

  Optional<Product> findByCartonBarcode(String cartonBarcode);

  /** Başka bir üründe (adet veya koli barkodu olarak) kullanılıyor mu. */
  @Query(
      """
      select count(p) > 0 from Product p
      where (p.barcode = :barcode or p.cartonBarcode = :barcode) and p.id <> :exceptId
      """)
  boolean barcodeUsedByOther(@Param("barcode") String barcode, @Param("exceptId") Long exceptId);

  /**
   * Satış/fiş/alış ekranlarında yazılan veya okutulan değer: sırasıyla ürün kodu,
   * adet barkodu, koli barkodu aranır. Koli barkodu eşleşirse sonuç "koli" işaretlidir.
   * Bulunamazsa anlaşılır bir hata fırlatır.
   */
  default ScanResult resolveScan(String codeOrBarcode) {
    if (codeOrBarcode == null || codeOrBarcode.isBlank()) {
      throw new IllegalArgumentException("Ürün kodu/barkod boş olamaz.");
    }
    String key = codeOrBarcode.trim();
    return findByCode(key)
        .or(() -> findByBarcode(key))
        .map(p -> new ScanResult(p, false))
        .or(() -> findByCartonBarcode(key).map(p -> new ScanResult(p, true)))
        .orElseThrow(() -> new IllegalArgumentException("Ürün bulunamadı: " + key));
  }

  /**
   * Free-text search over name and code. Parameter binding keeps this a prepared
   * statement — no string concatenation into SQL.
   */
  @Query(
      """
      select p from Product p
      where lower(p.name) like lower(concat('%', :q, '%'))
         or lower(p.code) like lower(concat('%', :q, '%'))
      """)
  Page<Product> search(@Param("q") String q, Pageable pageable);
}
