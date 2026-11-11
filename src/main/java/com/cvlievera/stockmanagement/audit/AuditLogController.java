package com.cvlievera.stockmanagement.audit;

import com.cvlievera.stockmanagement.common.dto.ApiResponse;
import com.cvlievera.stockmanagement.common.dto.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/audit-logs")
@RequiredArgsConstructor
public class AuditLogController {

    private final AuditLogService auditLogService;

    /** Recomputes the hash chain; brokenAtId is null when the log is intact. */
    @GetMapping("/verify")
    public ResponseEntity<ApiResponse<java.util.Map<String, Object>>> verify() {
        Long broken = auditLogService.verifyChain();
        java.util.Map<String, Object> result = new java.util.HashMap<>();
        result.put("intact", broken == null);
        result.put("brokenAtId", broken);
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<AuditLogDTO.Response>>> getAll(
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @PageableDefault(size = 50, sort = "createdAt") Pageable pageable) {

        if (userId != null) {
            return ResponseEntity.ok(ApiResponse.ok(PageResponse.from(auditLogService.getLogsByUser(userId, pageable))));
        }
        if (from != null && to != null) {
            return ResponseEntity.ok(ApiResponse.ok(PageResponse.from(auditLogService.getLogsByDateRange(from, to, pageable))));
        }
        return ResponseEntity.ok(ApiResponse.ok(PageResponse.from(auditLogService.getAllLogs(pageable))));
    }
}
