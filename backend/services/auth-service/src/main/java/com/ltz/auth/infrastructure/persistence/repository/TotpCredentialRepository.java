package com.ltz.auth.infrastructure.persistence.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import com.ltz.auth.domain.entity.TotpCredential;
import com.ltz.auth.domain.enums.TotpStatus;

import jakarta.persistence.LockModeType;

public interface TotpCredentialRepository extends JpaRepository<TotpCredential, UUID> {

    /** Satır kilidi: aynı zaman adımının kodu eşzamanlı iki istekte iki kez kabul edilmez. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from TotpCredential c where c.accountId = :accountId")
    Optional<TotpCredential> findByAccountIdForUpdate(UUID accountId);

    boolean existsByAccountIdAndStatus(UUID accountId, TotpStatus status);
}
