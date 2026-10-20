package com.ltz.hardware.infrastructure.persistence.repository;

import com.ltz.hardware.domain.entity.HardwareCampaign;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface HardwareCampaignRepository extends JpaRepository<HardwareCampaign, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from HardwareCampaign c where c.id = :id")
    Optional<HardwareCampaign> findForUpdate(UUID id);

    @Query("""
            select c from HardwareCampaign c, HardwareProduct p
            where c.productId = p.id and p.status = 'ACTIVE' and c.status = 'ACTIVE'
              and c.startsAt <= :now and c.endsAt > :now
              and (:productId is null or c.productId = :productId)
            """)
    Page<HardwareCampaign> findVisible(Instant now, UUID productId, Pageable pageable);

    @Query("""
            select c from HardwareCampaign c, HardwareProduct p
            where c.id = :id and c.productId = p.id and p.status = 'ACTIVE' and c.status = 'ACTIVE'
              and c.startsAt <= :now and c.endsAt > :now
            """)
    Optional<HardwareCampaign> findVisibleById(UUID id, Instant now);
}
