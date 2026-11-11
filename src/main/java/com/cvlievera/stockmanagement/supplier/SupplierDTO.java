package com.cvlievera.stockmanagement.supplier;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

public class SupplierDTO {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Request {

        @NotBlank(message = "Supplier name is required")
        @Size(max = 150, message = "Supplier name must not exceed 150 characters")
        private String name;

        @Size(max = 150, message = "Contact person must not exceed 150 characters")
        private String contactPerson;

        @Email(message = "Email must be a valid email address")
        @Size(max = 150, message = "Email must not exceed 150 characters")
        private String email;

        @Size(max = 50, message = "Phone must not exceed 50 characters")
        private String phone;

        @Size(max = 255, message = "Address must not exceed 255 characters")
        private String address;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Response {
        private Long id;
        private String name;
        private String contactPerson;
        private String email;
        private String phone;
        private String address;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }
}
