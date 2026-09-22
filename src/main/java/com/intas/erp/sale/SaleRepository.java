package com.intas.erp.sale;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SaleRepository extends JpaRepository<Sale, Long> {

  Optional<Sale> findFirstByStatusOrderByIdAsc(Sale.Status status);

  List<Sale> findByStatusOrderByIdDesc(Sale.Status status);
}
