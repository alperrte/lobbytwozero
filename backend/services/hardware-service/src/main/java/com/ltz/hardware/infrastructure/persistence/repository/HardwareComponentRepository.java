package com.ltz.hardware.infrastructure.persistence.repository;

import com.ltz.hardware.domain.entity.HardwareComponent;
import com.ltz.hardware.domain.enums.ComponentCategory;
import java.util.List;
import java.util.UUID;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface HardwareComponentRepository extends JpaRepository<HardwareComponent, UUID> {
    interface CategoryCount {
        ComponentCategory getCategory();
        long getEntries();
        long getUnits();
        long getKnownCapacityGb();
        long getUnitsWithoutCapacity();
    }

    @Query("""
        select c.category as category, count(c) as entries, sum(c.quantity) as units,
            sum(case when c.specifications.capacityGb is null then 0
                else c.quantity * c.specifications.capacityGb end) as knownCapacityGb,
            sum(case when c.specifications.capacityGb is null then c.quantity else 0 end) as unitsWithoutCapacity
        from HardwareComponent c join HardwareProfile p on p.id = c.profileId
        where p.userId = :userId group by c.category
        """)
    List<CategoryCount> countsByCategory(UUID userId);
    Page<HardwareComponent> findAllByProfileId(UUID profileId, Pageable pageable);
    Optional<HardwareComponent> findByIdAndProfileId(UUID id, UUID profileId);
}
