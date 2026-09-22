package com.intas.erp.cari;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CustomerRepository extends JpaRepository<Customer, Long> {

  Optional<Customer> findByCode(String code);

  List<Customer> findAllByOrderByNameAsc();

  /** Ad veya kod üzerinde serbest arama (prepared statement). */
  @Query(
      """
      select c from Customer c
      where lower(c.name) like lower(concat('%', :q, '%'))
         or lower(c.code) like lower(concat('%', :q, '%'))
      order by c.name asc
      """)
  Page<Customer> search(@Param("q") String q, Pageable pageable);
}
