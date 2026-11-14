package com.ltz.hardware.infrastructure.persistence.repository;

import com.ltz.hardware.domain.entity.CatalogText;
import com.ltz.hardware.domain.entity.PcPartProduct;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.ObjectMapper;

@Repository
public class PcPartImportRepository {
    private static final String PC_PART_DATASET = "PC_PART_DATASET:";
    public enum Outcome { ADDED, UPDATED, UNCHANGED, SKIPPED }
    private record Existing(UUID product, String hash, String status, String variant) { }
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public PcPartImportRepository(JdbcTemplate jdbc, ObjectMapper json) { this.jdbc = jdbc; this.json = json; }

    public void lock() {
        jdbc.execute("SET LOCAL lock_timeout='5s'");
        jdbc.execute("SET LOCAL statement_timeout='30s'");
        jdbc.execute("SELECT pg_advisory_xact_lock(71826040)");
    }

    public Outcome upsert(PcPartProduct row, String revision, boolean dryRun) {
        var found = jdbc.query("""
            SELECT s.product_id,s.content_hash,p.status,p.catalog_variant FROM hardware_product_sources s
            LEFT JOIN hardware_products p ON p.id=s.product_id
            WHERE s.source_provider='PC_PART_DATASET' AND s.external_product_id=?
            """, (rs, index) -> new Existing(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3), rs.getString(4)), row.identity());
        Existing old = found.isEmpty() ? null : found.getFirst();
        if (old != null && (!"ACTIVE".equals(old.status()) || !(PC_PART_DATASET + row.identity()).equals(old.variant()))) {
            return Outcome.SKIPPED;
        }
        Outcome outcome = Outcome.ADDED;
        if (old != null) outcome = old.hash().equals(row.contentHash()) ? Outcome.UNCHANGED : Outcome.UPDATED;
        if (dryRun) return outcome;
        UUID id = old == null ? UUID.randomUUID() : old.product();
        if (outcome == Outcome.ADDED) insertProduct(id, row);
        else if (outcome == Outcome.UPDATED) updateProduct(id, row);
        if (outcome != Outcome.UNCHANGED) {
            UUID brand = brand(row.brand());
            jdbc.update("INSERT INTO hardware_product_brands(product_id,brand_id) VALUES (?,?) ON CONFLICT DO NOTHING", id, brand);
        }
        saveSource(id, row, revision);
        return outcome;
    }

    private UUID brand(String name) {
        String normalized = CatalogText.normalize(name, 100);
        var existing = jdbc.queryForList("SELECT id FROM hardware_brands WHERE normalized_name=?", UUID.class, normalized);
        if (!existing.isEmpty()) return existing.getFirst();
        jdbc.update("INSERT INTO hardware_brands(id,name,normalized_name) VALUES (?,?,?) ON CONFLICT DO NOTHING", UUID.randomUUID(), name, normalized);
        return jdbc.queryForObject("SELECT id FROM hardware_brands WHERE normalized_name=?", UUID.class, normalized);
    }

    private void insertProduct(UUID id, PcPartProduct row) {
        var specs = row.specifications();
        jdbc.update("""
            INSERT INTO hardware_products(id,category,model,normalized_model,catalog_variant,capacity_gb,clock_mhz,
                core_count,power_watts,memory_type,socket,interface_type,form_factor)
            VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)
            """, id, row.category().name(), row.model(), CatalogText.normalize(row.model(), 160), PC_PART_DATASET + row.identity(),
                specs.getCapacityGb(), specs.getClockMhz(), specs.getCoreCount(), specs.getPowerWatts(),
                specs.getMemoryType(), specs.getSocket(), specs.getInterfaceType(), specs.getFormFactor());
    }

    private void updateProduct(UUID id, PcPartProduct row) {
        var specs = row.specifications();
        int changed = jdbc.update("""
            UPDATE hardware_products SET model=?,normalized_model=?,capacity_gb=?,clock_mhz=?,core_count=?,
                power_watts=?,memory_type=?,socket=?,interface_type=?,form_factor=?,version=version+1,updated_at=now()
            WHERE id=? AND catalog_variant=? AND status='ACTIVE'
            """, row.model(), CatalogText.normalize(row.model(), 160), specs.getCapacityGb(), specs.getClockMhz(),
                specs.getCoreCount(), specs.getPowerWatts(), specs.getMemoryType(), specs.getSocket(),
                specs.getInterfaceType(), specs.getFormFactor(), id, PC_PART_DATASET + row.identity());
        if (changed != 1) throw new IllegalStateException("IMPORTED_PRODUCT_CHANGED");
    }

    private void saveSource(UUID id, PcPartProduct row, String revision) {
        jdbc.update("""
            INSERT INTO hardware_product_sources(id,product_id,source_provider,external_product_id,category,brand,
                locale,synced_at,quality,content_hash,typed_specs,source_revision)
            VALUES (?,?,'PC_PART_DATASET',?,?,?,'und',now(),'VERIFIED',?,CAST(? AS jsonb),?)
            ON CONFLICT(source_provider,external_product_id) DO UPDATE SET
                synced_at=now(),source_revision=excluded.source_revision,content_hash=excluded.content_hash,
                typed_specs=excluded.typed_specs,brand=excluded.brand,updated_at=now(),version=hardware_product_sources.version+1
            """, UUID.randomUUID(), id, row.identity(), row.category().name(), row.brand(), row.contentHash(),
                json.writeValueAsString(row.technical()), revision);
    }

    public void report(String revision, Object files, Object summary, boolean partial, Instant started) {
        jdbc.update("""
            INSERT INTO hardware_dataset_import_runs(id,source_revision,files,summary,status,started_at)
            VALUES (?,?,CAST(? AS jsonb),CAST(? AS jsonb),?,?)
            """, UUID.randomUUID(), revision, json.writeValueAsString(files), json.writeValueAsString(summary),
                partial ? "PARTIAL" : "SUCCEEDED", Timestamp.from(started));
    }
}
