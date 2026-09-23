package com.intas.erp.slip;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StockSlipRepository extends JpaRepository<StockSlip, Long> {

  Optional<StockSlip> findFirstByStatusOrderByIdAsc(StockSlip.Status status);

  List<StockSlip> findByStatusOrderByIdDesc(StockSlip.Status status);
}
