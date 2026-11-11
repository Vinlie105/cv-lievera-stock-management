package com.cvlievera.stockmanagement.audit;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

public class AuditLogDTO {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Response {
        private Long id;
        private Long userId;
        private String username;
        private String action;
        private String entityType;
        private Long entityId;
        private String changeSummary;
        private String ipAddress;
        private LocalDateTime createdAt;
    }
}
