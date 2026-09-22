package com.intas.erp.cari;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CariTransactionRepository extends JpaRepository<CariTransaction, Long> {

  List<CariTransaction> findByCustomerOrderByIdAsc(Customer customer);
}
