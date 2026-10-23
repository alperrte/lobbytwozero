package com.ltz.auth.infrastructure.persistence.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import com.ltz.auth.domain.entity.AccountDeletionRequest;

import jakarta.persistence.LockModeType;

public interface AccountDeletionRequestRepository extends JpaRepository<AccountDeletionRequest, UUID> {

    Optional<AccountDeletionRequest> findByAccountId(UUID accountId);

    /** Satır kilidi: görevlerin eşzamanlı onayında son görevi onaylayan tek işlem tamamlamayı yapar. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from AccountDeletionRequest r where r.id = :id")
    Optional<AccountDeletionRequest> findByIdForUpdate(UUID id);
}
