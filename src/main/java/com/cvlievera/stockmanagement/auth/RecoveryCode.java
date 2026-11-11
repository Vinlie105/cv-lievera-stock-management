package com.cvlievera.stockmanagement.auth;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "recovery_codes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RecoveryCode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "code_hash", nullable = false, length = 64)
    private String codeHash;

    @Builder.Default
    @Column(nullable = false)
    private boolean used = false;
}
