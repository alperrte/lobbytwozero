package com.ltz.performance.infrastructure.persistence.repository;

import com.ltz.performance.domain.entity.DeviceLink;
import com.ltz.performance.domain.enums.DeviceLinkStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeviceLinkRepository extends JpaRepository<DeviceLink, UUID> {
    Optional<DeviceLink> findByAuthDeviceId(UUID authDeviceId);

    /** Sahiplik sorguya gömülüdür: yabancı kayıt ile olmayan kayıt aynı şekilde "yok" döner (IDOR). */
    Optional<DeviceLink> findByIdAndUserId(UUID id, UUID userId);

    List<DeviceLink> findAllByUserIdOrderByLinkedAtAsc(UUID userId);

    long countByUserIdAndStatus(UUID userId, DeviceLinkStatus status);
}
