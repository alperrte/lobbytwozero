package com.ltz.hardware.infrastructure.persistence.repository;

import com.ltz.hardware.domain.entity.HardwareProduct;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import com.ltz.hardware.domain.enums.CatalogStatus;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface HardwareProductRepository extends JpaRepository<HardwareProduct, UUID>, JpaSpecificationExecutor<HardwareProduct> {
    Page<HardwareProduct> findByStatus(CatalogStatus status, Pageable pageable);
    Optional<HardwareProduct> findByIdAndStatus(UUID id, CatalogStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from HardwareProduct p where p.id = :id")
    Optional<HardwareProduct> findForUpdate(UUID id);

    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("select p from HardwareProduct p where p.id = :id and p.status = 'ACTIVE'")
    Optional<HardwareProduct> findActiveForSelection(UUID id);

    @Query("select distinct p from HardwareProduct p left join fetch p.brands where p.id in :ids")
    List<HardwareProduct> findWithBrands(Collection<UUID> ids);

    @Query(value = "SELECT EXISTS (SELECT 1 FROM hardware_product_brands WHERE product_id = :productId AND brand_id = :brandId)", nativeQuery = true)
    boolean supportsBrand(UUID productId, UUID brandId);
}
