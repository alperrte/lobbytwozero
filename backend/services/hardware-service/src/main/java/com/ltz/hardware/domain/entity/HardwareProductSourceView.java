package com.ltz.hardware.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Read-only view of {@code hardware_product_sources}. Only the columns that catalog search needs are mapped;
 * rows are written exclusively by {@code HardwareCatalogSourceRepository} and the PC part importer.
 */
@Entity
@Immutable
@Table(name = "hardware_product_sources")
public class HardwareProductSourceView {
    @Id private UUID id;
    @Column(name = "product_id") private UUID productId;
    @Column(nullable = false, length = 32) private String quality;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "typed_specs", nullable = false)
    private String typedSpecs;

    protected HardwareProductSourceView() { }
}
