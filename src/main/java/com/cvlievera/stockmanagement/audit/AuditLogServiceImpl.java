package com.cvlievera.stockmanagement.audit;

import com.cvlievera.stockmanagement.user.User;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.List;

/**
 * Tamper-evident audit trail: every row stores SHA-256(previousHash | content). Rows are appended under a
 * database advisory lock so the chain is strictly ordered, and a DB trigger forbids UPDATE/DELETE/TRUNCATE.
 */
@Service
@RequiredArgsConstructor
public class AuditLogServiceImpl implements AuditLogService {

    private static final String GENESIS = "GENESIS";
    private static final long CHAIN_LOCK_KEY = 7_301_001L;
    private static final int VERIFY_PAGE = 500;

    private final AuditLogRepository auditLogRepository;
    private final EntityManager entityManager;
    private final PlatformTransactionManager transactionManager;

    @Override
    @Async
    public void log(User user, String action, String entityType, Long entityId,
                    String changeSummary, String ipAddress) {
        append(AuditLog.builder()
                .user(user)
                .username(user != null ? user.getUsername() : "system")
                .action(action)
                .entityType(entityType)
                .entityId(entityId)
                .changeSummary(changeSummary)
                .ipAddress(ipAddress)
                .build());
    }

    @Override
    @Async
    public void log(String username, String action, String entityType, Long entityId, String changeSummary) {
        append(AuditLog.builder()
                .username(username)
                .action(action)
                .entityType(entityType)
                .entityId(entityId)
                .changeSummary(changeSummary)
                .build());
    }

    private void append(AuditLog entry) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            entityManager.createNativeQuery("select pg_advisory_xact_lock(" + CHAIN_LOCK_KEY + ")").getSingleResult();
            String prev = auditLogRepository.findTopByHashIsNotNullOrderByIdDesc()
                    .map(AuditLog::getHash).orElse(GENESIS);
            // Postgres stores microseconds; truncate so the recomputed hash matches what is read back
            entry.setCreatedAt(LocalDateTime.now().truncatedTo(ChronoUnit.MICROS));
            entry.setPrevHash(prev);
            entry.setHash(computeHash(entry));
            auditLogRepository.save(entry);
        });
    }

    @Override
    public Long verifyChain() {
        String expectedPrev = GENESIS;
        long lastId = 0;
        boolean first = true;
        while (true) {
            List<AuditLog> page = auditLogRepository
                    .findByHashIsNotNullAndIdGreaterThanOrderByIdAsc(lastId, PageRequest.of(0, VERIFY_PAGE));
            if (page.isEmpty()) {
                return null;
            }
            for (AuditLog row : page) {
                boolean linked = first || expectedPrev.equals(row.getPrevHash());
                if (!linked || !computeHash(row).equals(row.getHash())) {
                    return row.getId();
                }
                first = false;
                expectedPrev = row.getHash();
                lastId = row.getId();
            }
        }
    }

    static String computeHash(AuditLog e) {
        String payload = String.join("|",
                String.valueOf(e.getPrevHash()),
                String.valueOf(e.getUser() != null ? e.getUser().getId() : null),
                String.valueOf(e.getUsername()),
                String.valueOf(e.getAction()),
                String.valueOf(e.getEntityType()),
                String.valueOf(e.getEntityId()),
                String.valueOf(e.getChangeSummary()),
                String.valueOf(e.getIpAddress()),
                String.valueOf(e.getCreatedAt()));
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    @Override
    public Page<AuditLogDTO.Response> getAllLogs(Pageable pageable) {
        return auditLogRepository.findAll(pageable).map(this::toResponse);
    }

    @Override
    public Page<AuditLogDTO.Response> getLogsByUser(Long userId, Pageable pageable) {
        return auditLogRepository.findByUserId(userId, pageable).map(this::toResponse);
    }

    @Override
    public Page<AuditLogDTO.Response> getLogsByDateRange(LocalDateTime from, LocalDateTime to, Pageable pageable) {
        return auditLogRepository.findByCreatedAtBetween(from, to, pageable).map(this::toResponse);
    }

    private AuditLogDTO.Response toResponse(AuditLog log) {
        return AuditLogDTO.Response.builder()
                .id(log.getId())
                .userId(log.getUser() != null ? log.getUser().getId() : null)
                .username(log.getUsername())
                .action(log.getAction())
                .entityType(log.getEntityType())
                .entityId(log.getEntityId())
                .changeSummary(log.getChangeSummary())
                .ipAddress(log.getIpAddress())
                .createdAt(log.getCreatedAt())
                .build();
    }
}
