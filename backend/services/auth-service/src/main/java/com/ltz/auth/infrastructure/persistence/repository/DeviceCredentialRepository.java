package com.ltz.auth.infrastructure.persistence.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import com.ltz.auth.domain.entity.DeviceCredential;
import com.ltz.auth.domain.enums.DeviceRevokeReason;

public interface DeviceCredentialRepository extends JpaRepository<DeviceCredential, UUID> {

    Optional<DeviceCredential> findByIdAndAccountId(UUID id, UUID accountId);

    @Query("""
            select d from DeviceCredential d
            where d.accountId = :accountId and d.status = com.ltz.auth.domain.enums.DeviceStatus.ACTIVE
              and d.expiresAt > :now
            order by d.createdAt desc
            """)
    List<DeviceCredential> findUsable(UUID accountId, Instant now);

    @Query("""
            select count(d) from DeviceCredential d
            where d.accountId = :accountId and d.status = com.ltz.auth.domain.enums.DeviceStatus.ACTIVE
              and d.expiresAt > :now
            """)
    long countUsable(UUID accountId, Instant now);

    long countByAccountIdAndCreatedAtAfter(UUID accountId, Instant since);

    /** Hesap güvenliği değiştiğinde (parola, e-posta, askı, silme) hesabın tüm cihazları aynı işlemde iptal edilir. */
    @Modifying(flushAutomatically = true)
    @Query("""
            update DeviceCredential d
            set d.status = com.ltz.auth.domain.enums.DeviceStatus.REVOKED, d.revokedAt = :now, d.revokeReason = :reason
            where d.accountId = :accountId and d.status = com.ltz.auth.domain.enums.DeviceStatus.ACTIVE
            """)
    int revokeAllActive(UUID accountId, DeviceRevokeReason reason, Instant now);
}
