package com.ltz.auth.infrastructure.persistence.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import com.ltz.auth.domain.entity.DeletionSupportRequest;
import com.ltz.auth.domain.enums.DeletionSupportStatus;

import jakarta.persistence.LockModeType;

public interface DeletionSupportRequestRepository extends JpaRepository<DeletionSupportRequest, UUID> {

    Optional<DeletionSupportRequest> findFirstByAccountIdAndStatus(UUID accountId, DeletionSupportStatus status);

    Optional<DeletionSupportRequest> findFirstByAccountIdOrderByCreatedAtDesc(UUID accountId);

    /** En eski talep önce: yönetici sırayı bozmadan işler. */
    List<DeletionSupportRequest> findByStatusOrderByCreatedAtAsc(DeletionSupportStatus status, Pageable pageable);

    /** Karar eşzamanlı iki yöneticiden yalnız birine işler. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from DeletionSupportRequest r where r.id = :id")
    Optional<DeletionSupportRequest> findByIdForUpdate(UUID id);
}
