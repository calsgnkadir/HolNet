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

  /**
   * Satış/fiş/alış ekranlarında yazılan veya okutulan değer: önce ürün kodu, sonra
   * barkod aranır. Bulunamazsa anlaşılır bir hata fırlatır.
   */
  default Product requireByCodeOrBarcode(String codeOrBarcode) {
    if (codeOrBarcode == null || codeOrBarcode.isBlank()) {
      throw new IllegalArgumentException("Ürün kodu/barkod boş olamaz.");
    }
    String key = codeOrBarcode.trim();
    return findByCode(key)
        .or(() -> findByBarcode(key))
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
