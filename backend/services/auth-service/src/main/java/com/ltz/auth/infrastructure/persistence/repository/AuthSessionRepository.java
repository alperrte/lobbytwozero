package com.ltz.auth.infrastructure.persistence.repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import com.ltz.auth.domain.entity.AuthSession;
import com.ltz.auth.domain.enums.SessionRevokeReason;

import jakarta.persistence.LockModeType;

public interface AuthSessionRepository extends JpaRepository<AuthSession, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from AuthSession s where s.id = :id")
    Optional<AuthSession> findByIdForUpdate(UUID id);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update AuthSession s set s.revokedAt = :now, s.revokeReason = :reason
            where s.accountId = :accountId and s.revokedAt is null
            """)
    int revokeAllActive(UUID accountId, SessionRevokeReason reason, Instant now);
}
