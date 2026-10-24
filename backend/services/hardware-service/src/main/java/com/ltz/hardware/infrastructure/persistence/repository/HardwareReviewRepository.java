package com.ltz.hardware.infrastructure.persistence.repository;

import com.ltz.hardware.domain.entity.HardwareReview;
import com.ltz.hardware.domain.enums.HardwareReviewStatus;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;

public interface HardwareReviewRepository extends JpaRepository<HardwareReview, UUID>, JpaSpecificationExecutor<HardwareReview> {
    Page<HardwareReview> findAllByUserIdAndStatusNot(UUID userId, HardwareReviewStatus status, Pageable pageable);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from HardwareReview r where r.id = :id and r.userId = :userId and r.status <> 'DELETED'")
    Optional<HardwareReview> findOwnedForUpdate(UUID id, UUID userId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from HardwareReview r where r.id = :id and r.status <> 'DELETED'")
    Optional<HardwareReview> findForUpdate(UUID id);
}
