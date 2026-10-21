package com.ltz.hardware.infrastructure.persistence.repository;

import com.ltz.hardware.domain.entity.HardwareProfile;
import com.ltz.hardware.domain.enums.DeviceType;
import java.util.List;
import java.util.UUID;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HardwareProfileRepository extends JpaRepository<HardwareProfile, UUID> {
    interface DeviceTypeCount {
        DeviceType getDeviceType();
        long getTotal();
    }

    @Query("select p.deviceType as deviceType, count(p) as total from HardwareProfile p where p.userId = :userId group by p.deviceType")
    List<DeviceTypeCount> countsByDeviceType(UUID userId);
    Optional<HardwareProfile> findByIdAndUserId(UUID id, UUID userId);
    List<HardwareProfile> findAllByIdInAndUserId(java.util.Collection<UUID> ids, UUID userId);
    Page<HardwareProfile> findAllByUserId(UUID userId, Pageable pageable);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from HardwareProfile p where p.id = :id and p.userId = :userId")
    Optional<HardwareProfile> findOwnedForUpdate(UUID id, UUID userId);
}
