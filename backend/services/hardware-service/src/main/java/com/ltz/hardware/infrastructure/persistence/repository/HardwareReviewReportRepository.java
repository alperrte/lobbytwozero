package com.ltz.hardware.infrastructure.persistence.repository;

import com.ltz.hardware.domain.entity.HardwareReviewReport;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HardwareReviewReportRepository extends JpaRepository<HardwareReviewReport, UUID> {
    boolean existsByReviewIdAndReporterId(UUID reviewId, UUID reporterId);
    Optional<HardwareReviewReport> findByIdAndReporterId(UUID id, UUID reporterId);
    Page<HardwareReviewReport> findAllByReviewId(UUID reviewId, Pageable pageable);
}
