package com.ltz.hardware.domain.entity;

import com.ltz.hardware.domain.enums.ComponentCategory;
import jakarta.persistence.*;
import java.util.UUID;
import lombok.Getter;

/** Minimal read model for profile selection. Catalog management belongs to task 02. */
@Entity
@Table(name = "hardware_products")
@Getter
public class HardwareProduct {
    @Id private UUID id;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ComponentCategory category;
    @Column(nullable = false, length = 160)
    private String model;
    @Column(name = "normalized_model", nullable = false, length = 160)
    private String normalizedModel;
    @Embedded private HardwareSpecifications specifications;
    @Version private long version;
    protected HardwareProduct() { }
}
