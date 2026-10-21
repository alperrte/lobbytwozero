package com.ltz.auth.infrastructure.persistence.repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;

import com.ltz.auth.domain.entity.AccountDeletionTask;
import com.ltz.auth.domain.enums.DeletionTaskStatus;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;

public interface AccountDeletionTaskRepository extends JpaRepository<AccountDeletionTask, UUID> {

    /** SKIP LOCKED (-2): eşzamanlı işlemciler aynı görevi almaz, birbirini beklemez. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    @Query("""
            select t from AccountDeletionTask t
            where t.status = :status and t.nextAttemptAt <= :now
            order by t.nextAttemptAt
            """)
    List<AccountDeletionTask> findDue(DeletionTaskStatus status, Instant now, Pageable page);

    long countByRequestIdAndStatus(UUID requestId, DeletionTaskStatus status);
}
