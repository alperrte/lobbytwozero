package com.ltz.auth.infrastructure.persistence.repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;

import com.ltz.auth.domain.entity.AuthEvent;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;

public interface AuthEventRepository extends JpaRepository<AuthEvent, UUID> {

    /** SKIP LOCKED (-2): eşzamanlı işlemciler aynı olayı almaz, birbirini beklemez. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    @Query("""
            select e from AuthEvent e
            where e.status = com.ltz.auth.domain.enums.AuthEventStatus.PENDING and e.nextAttemptAt <= :now
            order by e.nextAttemptAt
            """)
    List<AuthEvent> findDue(Instant now, Pageable page);

    /** Teslim edilmiş satırlar kısa süre tutulur (hata ayıklama için), sonra silinir. */
    @Modifying(flushAutomatically = true)
    @Query("""
            delete from AuthEvent e
            where e.status = com.ltz.auth.domain.enums.AuthEventStatus.DELIVERED and e.deliveredAt < :cutoff
            """)
    int deleteDeliveredBefore(Instant cutoff);
}
