package com.cvlievera.stockmanagement.inventory;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

public class StockTransactionDTO {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Request {

        @NotNull(message = "Product ID is required")
        private Long productId;

        @NotNull(message = "Transaction type is required")
        private StockTransaction.TransactionType transactionType;

        @NotNull(message = "Quantity is required")
        private Integer quantity;

        private String notes;
        private String referenceNo;
        private LocalDateTime transactionDate;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Response {
        private Long id;
        private Long productId;
        private String productName;
        private String productSku;
        private StockTransaction.TransactionType transactionType;
        private Integer quantity;
        private String notes;
        private String referenceNo;
        private LocalDateTime transactionDate;
        private String performedByUsername;
        private LocalDateTime createdAt;
    }
}
