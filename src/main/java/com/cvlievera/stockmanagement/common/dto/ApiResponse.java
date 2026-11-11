package com.cvlievera.stockmanagement.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

    private boolean success;
    private T data;
    private String error;
    private Map<String, String> fieldErrors;

    public static <T> ApiResponse<T> ok(T data) {
        return ApiResponse.<T>builder().success(true).data(data).build();
    }

    public static <T> ApiResponse<T> error(String message) {
        return ApiResponse.<T>builder().success(false).error(message).build();
    }

    public static <T> ApiResponse<T> validationError(Map<String, String> fieldErrors) {
        return ApiResponse.<T>builder()
                .success(false)
                .error("Validation failed")
                .fieldErrors(fieldErrors)
                .build();
    }
}
