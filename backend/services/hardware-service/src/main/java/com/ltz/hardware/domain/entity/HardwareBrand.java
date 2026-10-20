package com.ltz.hardware.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.util.UUID;
import lombok.Getter;

@Entity
@Table(name = "hardware_brands")
@Getter
public class HardwareBrand {
    @Id private UUID id;
    @Column(nullable = false, length = 100)
    private String name;
    @Column(name = "normalized_name", nullable = false, length = 100)
    private String normalizedName;
    @Version private long version;
    protected HardwareBrand() { }
}
