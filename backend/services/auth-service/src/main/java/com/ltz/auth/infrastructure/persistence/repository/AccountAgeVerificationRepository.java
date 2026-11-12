package com.ltz.auth.infrastructure.persistence.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import com.ltz.auth.domain.entity.AccountAgeVerification;

import jakarta.persistence.LockModeType;

public interface AccountAgeVerificationRepository extends JpaRepository<AccountAgeVerification, UUID> {

    /** Access token üretimi: geri alınmamış doğrulama var mı (tek PK sorgusu). */
    boolean existsByAccountIdAndRevokedAtIsNull(UUID accountId);

    /** Satır kilidi: eşzamanlı doğrulama/geri alma isteklerinde tek sonuç. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from AccountAgeVerification v where v.accountId = :accountId")
    Optional<AccountAgeVerification> findByAccountIdForUpdate(UUID accountId);
}
