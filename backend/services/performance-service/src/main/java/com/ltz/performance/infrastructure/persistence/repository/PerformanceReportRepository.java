package com.ltz.performance.infrastructure.persistence.repository;

import com.ltz.performance.domain.entity.PerformanceReport;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PerformanceReportRepository extends JpaRepository<PerformanceReport, UUID> {
    /** Sahiplik sorguya gömülüdür: yabancı rapor ile olmayan rapor aynı şekilde "yok" döner (IDOR). */
    Optional<PerformanceReport> findBySessionIdAndUserId(UUID sessionId, UUID userId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from PerformanceReport r where r.sessionId = :sessionId and r.userId = :userId")
    int deleteOwned(@Param("sessionId") UUID sessionId, @Param("userId") UUID userId);
}
