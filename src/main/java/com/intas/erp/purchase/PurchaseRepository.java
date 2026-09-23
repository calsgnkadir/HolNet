package com.intas.erp.purchase;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PurchaseRepository extends JpaRepository<Purchase, Long> {

  Optional<Purchase> findFirstByStatusOrderByIdAsc(Purchase.Status status);

  List<Purchase> findByStatusOrderByIdDesc(Purchase.Status status);
}
