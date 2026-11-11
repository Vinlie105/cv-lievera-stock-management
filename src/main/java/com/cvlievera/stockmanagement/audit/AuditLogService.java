package com.cvlievera.stockmanagement.audit;

import com.cvlievera.stockmanagement.user.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;

public interface AuditLogService {

    void log(User user, String action, String entityType, Long entityId, String changeSummary, String ipAddress);

    void log(String username, String action, String entityType, Long entityId, String changeSummary);

    Page<AuditLogDTO.Response> getAllLogs(Pageable pageable);

    Page<AuditLogDTO.Response> getLogsByUser(Long userId, Pageable pageable);

    Page<AuditLogDTO.Response> getLogsByDateRange(LocalDateTime from, LocalDateTime to, Pageable pageable);

    /** @return id of the first tampered/missing entry, or null when the whole chain verifies. */
    Long verifyChain();
}
