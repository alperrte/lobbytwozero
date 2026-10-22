package com.ltz.auth.infrastructure.persistence.repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import com.ltz.auth.domain.entity.AccountDeletionToken;
import com.ltz.auth.domain.enums.CodeInvalidationReason;

import jakarta.persistence.LockModeType;

public interface AccountDeletionTokenRepository extends JpaRepository<AccountDeletionToken, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from AccountDeletionToken t where t.tokenHash = :tokenHash")
    Optional<AccountDeletionToken> findByTokenHashForUpdate(String tokenHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from AccountDeletionToken t where t.id = :id")
    Optional<AccountDeletionToken> findByIdForUpdate(UUID id);

    Optional<AccountDeletionToken> findFirstByAccountIdOrderByCreatedAtDesc(UUID accountId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update AccountDeletionToken t set t.invalidatedAt = :now, t.invalidationReason = :reason
            where t.accountId = :accountId and t.consumedAt is null and t.invalidatedAt is null
            """)
    int invalidateActive(UUID accountId, CodeInvalidationReason reason, Instant now);
}
