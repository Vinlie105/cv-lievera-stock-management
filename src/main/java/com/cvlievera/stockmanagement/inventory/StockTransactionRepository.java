package com.cvlievera.stockmanagement.inventory;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface StockTransactionRepository extends JpaRepository<StockTransaction, Long> {

    Page<StockTransaction> findByProductId(Long productId, Pageable pageable);

    Page<StockTransaction> findByTransactionType(StockTransaction.TransactionType type, Pageable pageable);

    List<StockTransaction> findByTransactionDateBetween(LocalDateTime from, LocalDateTime to);
}
