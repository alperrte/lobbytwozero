package com.ltz.auth.infrastructure.persistence.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import com.ltz.auth.domain.entity.RecoveryCode;

import jakarta.persistence.LockModeType;

public interface RecoveryCodeRepository extends JpaRepository<RecoveryCode, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select c from RecoveryCode c
            where c.accountId = :accountId and c.codeHash = :codeHash and c.usedAt is null
            """)
    Optional<RecoveryCode> findUnusedForUpdate(UUID accountId, String codeHash);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from RecoveryCode c where c.accountId = :accountId")
    int deleteAllByAccountId(UUID accountId);
}
