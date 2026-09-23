package com.intas.erp.product;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StockMovementRepository extends JpaRepository<StockMovement, Long> {

  List<StockMovement> findByProductOrderByIdDesc(Product product);
}
