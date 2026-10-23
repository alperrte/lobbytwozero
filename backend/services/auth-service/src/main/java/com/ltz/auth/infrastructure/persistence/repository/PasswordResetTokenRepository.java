package com.ltz.auth.infrastructure.persistence.repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import com.ltz.auth.domain.entity.PasswordResetToken;
import com.ltz.auth.domain.enums.PasswordResetInvalidationReason;

import jakarta.persistence.LockModeType;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, UUID> {

    @Query("select t from PasswordResetToken t where t.tokenHash = :tokenHash")
    Optional<PasswordResetToken> findByTokenHash(String tokenHash);

    /** Satır kilidi: aynı token ile eşzamanlı iki sıfırlamadan yalnız biri başarılı olur. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from PasswordResetToken t where t.tokenHash = :tokenHash")
    Optional<PasswordResetToken> findByTokenHashForUpdate(String tokenHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from PasswordResetToken t where t.id = :id")
    Optional<PasswordResetToken> findByIdForUpdate(UUID id);

    Optional<PasswordResetToken> findFirstByAccountIdOrderByCreatedAtDesc(UUID accountId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update PasswordResetToken t set t.invalidatedAt = :now, t.invalidationReason = :reason
            where t.accountId = :accountId and t.consumedAt is null and t.invalidatedAt is null
            """)
    int invalidateActive(UUID accountId, PasswordResetInvalidationReason reason, Instant now);
}
