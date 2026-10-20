package com.ltz.hardware.infrastructure.persistence.repository;

import com.ltz.hardware.domain.entity.HardwareBrand;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HardwareBrandRepository extends JpaRepository<HardwareBrand, UUID> { }
