package com.cvlievera.stockmanagement.audit;

import com.cvlievera.stockmanagement.user.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "audit_logs")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(length = 100)
    private String username;

    @Column(nullable = false, length = 100)
    private String action;

    @Column(length = 100)
    private String entityType;

    private Long entityId;

    @Column(columnDefinition = "TEXT")
    private String changeSummary;

    @Column(length = 45)
    private String ipAddress;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "prev_hash", length = 64, updatable = false)
    private String prevHash;

    @Column(name = "hash", length = 64, updatable = false)
    private String hash;
}
