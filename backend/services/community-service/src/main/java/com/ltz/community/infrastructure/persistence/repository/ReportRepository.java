package com.ltz.community.infrastructure.persistence.repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ltz.community.domain.entity.Report;
import com.ltz.community.domain.enums.ReportStatus;
import com.ltz.community.domain.enums.ReportTarget;

public interface ReportRepository extends JpaRepository<Report, UUID> {

	boolean existsByTargetTypeAndTargetIdAndReporterId(ReportTarget targetType, UUID targetId, UUID reporterId);

	long countByReporterIdAndCreatedAtAfter(UUID reporterId, Instant after);

	List<Report> findByTargetTypeAndTargetIdAndStatus(ReportTarget targetType, UUID targetId, ReportStatus status);
}
