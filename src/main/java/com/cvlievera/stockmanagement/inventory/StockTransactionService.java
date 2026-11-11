package com.cvlievera.stockmanagement.inventory;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface StockTransactionService {

    Page<StockTransactionDTO.Response> getAllTransactions(Pageable pageable);

    Page<StockTransactionDTO.Response> getTransactionsByProduct(Long productId, Pageable pageable);

    Page<StockTransactionDTO.Response> getTransactionsByType(StockTransaction.TransactionType type, Pageable pageable);

    StockTransactionDTO.Response createTransaction(StockTransactionDTO.Request request);
}
