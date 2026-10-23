package com.ltz.hardware.infrastructure.persistence.repository;

import com.ltz.hardware.domain.entity.HardwareBrand;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import com.ltz.hardware.domain.enums.CatalogStatus;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface HardwareBrandRepository extends JpaRepository<HardwareBrand, UUID> {
    Page<HardwareBrand> findByStatus(CatalogStatus status, Pageable pageable);
    Optional<HardwareBrand> findByIdAndStatus(UUID id, CatalogStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from HardwareBrand b where b.id = :id")
    Optional<HardwareBrand> findForUpdate(UUID id);

    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("select b from HardwareBrand b where b.id in :ids and b.status = 'ACTIVE' order by b.id")
    List<HardwareBrand> findActiveForSelection(Collection<UUID> ids);
}
