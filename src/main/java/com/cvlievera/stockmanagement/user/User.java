package com.cvlievera.stockmanagement.user;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String username;

    @Column(name = "password_hash", nullable = false)
    @ToString.Exclude
    private String passwordHash;

    @Column(nullable = false, length = 150)
    private String fullName;

    @Column(unique = true, length = 150)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    @Builder.Default
    @Column(nullable = false)
    private boolean active = true;

    private LocalDateTime lastLoginAt;

    @Builder.Default
    @Column(nullable = false)
    private int failedAttempts = 0;

    private LocalDateTime lockedUntil;

    @Builder.Default
    @Column(nullable = false)
    private boolean mustChangePassword = false;

    private LocalDateTime passwordChangedAt;

    /** Incremented on password change / deactivation / 2FA reset; invalidates outstanding access tokens. */
    @Builder.Default
    @Column(nullable = false)
    private int tokenVersion = 0;

    @Column(name = "totp_secret_enc")
    @ToString.Exclude
    private String totpSecretEnc;

    @Builder.Default
    @Column(nullable = false)
    private boolean totpEnabled = false;

    private Long totpLastStep;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
