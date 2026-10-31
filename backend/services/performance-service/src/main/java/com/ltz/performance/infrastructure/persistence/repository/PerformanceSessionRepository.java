package com.ltz.performance.infrastructure.persistence.repository;

import com.ltz.performance.domain.entity.PerformanceSession;
import com.ltz.performance.domain.enums.SessionStatus;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PerformanceSessionRepository
        extends JpaRepository<PerformanceSession, UUID>, JpaSpecificationExecutor<PerformanceSession> {
    /** Sahiplik sorguya gömülüdür: yabancı kayıt ile olmayan kayıt aynı şekilde "yok" döner (IDOR). */
    Optional<PerformanceSession> findByIdAndUserId(UUID id, UUID userId);

    Optional<PerformanceSession> findFirstByUserIdAndStatus(UUID userId, SessionStatus status);

    boolean existsByUserIdAndStatus(UUID userId, SessionStatus status);

    long countByUserId(UUID userId);

    /** Rapor satırı veritabanındaki ON DELETE CASCADE ile silinir. Silinen satır sayısı döner. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from PerformanceSession s where s.id = :id and s.userId = :userId")
    int deleteOwned(@Param("id") UUID id, @Param("userId") UUID userId);
}
