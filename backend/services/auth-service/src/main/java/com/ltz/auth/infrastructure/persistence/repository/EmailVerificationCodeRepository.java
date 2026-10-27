package com.ltz.auth.infrastructure.persistence.repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import com.ltz.auth.domain.entity.EmailVerificationCode;
import com.ltz.auth.domain.enums.VerificationPurpose;

import jakarta.persistence.LockModeType;

public interface EmailVerificationCodeRepository extends JpaRepository<EmailVerificationCode, UUID> {

    /** Satır kilidi: eşzamanlı iki doğrulamadan yalnız biri kodu tüketebilir. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select c from EmailVerificationCode c
            where c.accountId = :accountId and c.purpose = :purpose
              and c.consumedAt is null and c.invalidatedAt is null
            """)
    Optional<EmailVerificationCode> findActiveForUpdate(UUID accountId, VerificationPurpose purpose);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from EmailVerificationCode c where c.id = :id")
    Optional<EmailVerificationCode> findByIdForUpdate(UUID id);

    Optional<EmailVerificationCode> findFirstByAccountIdAndPurposeOrderByCreatedAtDesc(
            UUID accountId, VerificationPurpose purpose);

    long countByAccountIdAndPurposeAndCreatedAtAfter(UUID accountId, VerificationPurpose purpose, Instant since);
}
