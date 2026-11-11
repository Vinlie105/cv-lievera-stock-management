package com.cvlievera.stockmanagement.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RecoveryCodeRepository extends JpaRepository<RecoveryCode, Long> {

    Optional<RecoveryCode> findByUserIdAndCodeHashAndUsedFalse(Long userId, String codeHash);

    @Modifying
    @Query("delete from RecoveryCode c where c.userId = :userId")
    void deleteAllForUser(@Param("userId") Long userId);
}
