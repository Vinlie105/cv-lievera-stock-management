package com.cvlievera.stockmanagement.audit;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    java.util.Optional<AuditLog> findTopByHashIsNotNullOrderByIdDesc();

    java.util.List<AuditLog> findByHashIsNotNullAndIdGreaterThanOrderByIdAsc(Long id, Pageable pageable);

    Page<AuditLog> findByUserId(Long userId, Pageable pageable);

    Page<AuditLog> findByEntityTypeAndEntityId(String entityType, Long entityId, Pageable pageable);

    Page<AuditLog> findByCreatedAtBetween(LocalDateTime from, LocalDateTime to, Pageable pageable);
}
