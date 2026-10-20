package com.ltz.hardware.infrastructure.persistence.repository;

import com.ltz.hardware.domain.entity.HardwareProduct;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface HardwareProductRepository extends JpaRepository<HardwareProduct, UUID> {
    @Query(value = "SELECT EXISTS (SELECT 1 FROM hardware_product_brands WHERE product_id = :productId AND brand_id = :brandId)", nativeQuery = true)
    boolean supportsBrand(UUID productId, UUID brandId);
}
