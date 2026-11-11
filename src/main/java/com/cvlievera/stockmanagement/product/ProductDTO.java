package com.cvlievera.stockmanagement.product;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class ProductDTO {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Request {

        @NotBlank(message = "Product name is required")
        @Size(max = 150, message = "Product name must not exceed 150 characters")
        private String name;

        @NotBlank(message = "SKU is required")
        @Size(max = 100, message = "SKU must not exceed 100 characters")
        private String sku;

        @Size(max = 255, message = "Description must not exceed 255 characters")
        private String description;

        @NotNull(message = "Unit price is required")
        @DecimalMin(value = "0.0", inclusive = false, message = "Unit price must be greater than 0")
        @Digits(integer = 13, fraction = 2, message = "Unit price must have at most 13 integer digits and 2 decimal places")
        private BigDecimal unitPrice;

        @DecimalMin(value = "0.0", inclusive = true, message = "Cost price must not be negative")
        @Digits(integer = 13, fraction = 2, message = "Cost price must have at most 13 integer digits and 2 decimal places")
        private BigDecimal costPrice;

        @NotNull(message = "Quantity in stock is required")
        @Min(value = 0, message = "Quantity in stock must not be negative")
        private Integer quantityInStock;

        @NotNull(message = "Minimum stock level is required")
        @Min(value = 0, message = "Minimum stock level must not be negative")
        private Integer minStockLevel;

        private Unit unit;

        @Size(max = 100, message = "Brand must not exceed 100 characters")
        private String brand;

        @Size(max = 100, message = "Barcode must not exceed 100 characters")
        private String barcode;

        private boolean expiryTracked;

        private Long categoryId;
        private Long supplierId;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Response {
        private Long id;
        private String name;
        private String sku;
        private String description;
        private BigDecimal unitPrice;
        private BigDecimal costPrice;
        private Integer quantityInStock;
        private Integer minStockLevel;
        private Unit unit;
        private String brand;
        private String barcode;
        private boolean active;
        private boolean expiryTracked;
        private boolean lowStock;
        private String categoryName;
        private String supplierName;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class StockResponse {
        private Long productId;
        private String name;
        private String sku;
        private Unit unit;
        private Integer quantityInStock;
        private Integer minStockLevel;
        private boolean lowStock;
        private boolean active;
    }
}
