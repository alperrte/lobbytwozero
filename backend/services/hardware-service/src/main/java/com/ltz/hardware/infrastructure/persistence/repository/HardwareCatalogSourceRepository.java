package com.ltz.hardware.infrastructure.persistence.repository;

import com.ltz.hardware.api.dto.response.HardwareCatalogSourceResponse;
import com.ltz.hardware.api.dto.response.HardwareCatalogSourceResponse.Image;
import com.ltz.hardware.domain.entity.HardwareCatalogContent;
import com.ltz.hardware.domain.entity.HardwareCatalogContent.Group;
import com.ltz.hardware.domain.entity.HardwareCatalogContent.Value;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.ObjectMapper;

@Repository
public class HardwareCatalogSourceRepository {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    public HardwareCatalogSourceRepository(JdbcTemplate jdbc, ObjectMapper mapper) { this.jdbc = jdbc; this.mapper = mapper; }
    public record Existing(UUID id, UUID productId, String brand, String mpn, String hash, String quality) { }
    public Existing find(String externalId) {
        var rows = jdbc.query("""
            SELECT id,product_id,brand,mpn,content_hash,quality FROM hardware_product_sources
            WHERE source_provider='OPEN_ICECAT' AND external_product_id=? FOR UPDATE
            """, (rs, row) -> new Existing(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class),
                rs.getString(3), rs.getString(4), rs.getString(5), rs.getString(6)), externalId);
        return rows.isEmpty() ? null : rows.getFirst();
    }
    public boolean conflicts(HardwareCatalogContent content) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
            SELECT EXISTS(SELECT 1 FROM hardware_product_sources WHERE quality='VERIFIED'
                AND external_product_id<>? AND ((lower(brand)=lower(?) AND mpn IS NOT NULL AND lower(mpn)=lower(?))
                    OR gtins ?| CAST(? AS text[])))
            """, Boolean.class, content.externalId(), content.brand(), content.mpn(),
                "{" + String.join(",", content.gtins()) + "}"));
    }
    public void save(UUID sourceId, UUID productId, HardwareCatalogContent content) {
        jdbc.update("""
            INSERT INTO hardware_product_sources(id,product_id,source_provider,external_product_id,category,
                brand,mpn,gtins,locale,source_updated_at,synced_at,quality,content_hash,typed_specs,feature_groups)
            VALUES (?,?,'OPEN_ICECAT',?,?,?,?,CAST(? AS jsonb),?,?,now(),'VERIFIED',?,CAST(? AS jsonb),CAST(? AS jsonb))
            ON CONFLICT(source_provider,external_product_id) DO UPDATE SET
                product_id=excluded.product_id,brand=excluded.brand,mpn=excluded.mpn,gtins=excluded.gtins,
                source_updated_at=excluded.source_updated_at,synced_at=now(),quality='VERIFIED',issue_code=NULL,
                content_hash=excluded.content_hash,typed_specs=excluded.typed_specs,feature_groups=excluded.feature_groups,
                updated_at=now(),version=hardware_product_sources.version+1
            """, sourceId, productId, content.externalId(), content.category().name(), content.brand(), content.mpn(),
                mapper.writeValueAsString(content.gtins()), content.locale(), Timestamp.from(content.updatedAt() == null
                    ? java.time.Instant.EPOCH : content.updatedAt()), content.contentHash(),
                mapper.writeValueAsString(content.typedSpecs()), mapper.writeValueAsString(content.featureGroups()));
    }
    public void quarantine(HardwareCatalogContent content, String code) {
        var existing = find(content.externalId());
        if (existing == null) {
            jdbc.update("""
                INSERT INTO hardware_product_sources(id,source_provider,external_product_id,category,brand,mpn,
                    locale,synced_at,quality,issue_code,content_hash)
                VALUES (?,'OPEN_ICECAT',?,?,?,?,?,now(),'QUARANTINED',?,?)
                """, UUID.randomUUID(), content.externalId(), content.category().name(), content.brand(), content.mpn(),
                    content.locale(), code, content.contentHash());
        } else mark(content.externalId(), "QUARANTINED", code);
    }
    public void mark(String externalId, String quality, String code) {
        jdbc.update("""
            UPDATE hardware_product_sources SET quality=?,issue_code=?,updated_at=now(),version=version+1
            WHERE external_product_id=? AND source_provider='OPEN_ICECAT'
            """, quality, code, externalId);
        jdbc.update("""
            UPDATE hardware_products SET catalog_published=false,version=version+1,updated_at=now()
            WHERE id IN (SELECT product_id FROM hardware_product_sources WHERE external_product_id=?)
            """, externalId);
    }
    public void refreshed(String externalId) {
        jdbc.update("UPDATE hardware_product_sources SET synced_at=now() WHERE external_product_id=?", externalId);
    }
    public Map<UUID, HardwareCatalogSourceResponse> load(Collection<UUID> productIds) {
        if (productIds.isEmpty()) return Map.of();
        String slots = String.join(",", java.util.Collections.nCopies(productIds.size(), "?"));
        var arguments = productIds.toArray();
        var images = new HashMap<UUID, List<Image>>();
        jdbc.query("""
            SELECT s.product_id,m.id,m.alt,m.width,m.height,m.is_primary FROM hardware_product_media m
            JOIN hardware_product_sources s ON s.id=m.source_id WHERE s.quality='VERIFIED' AND m.rights_verified
            AND s.product_id IN (""" + slots + ")", rs -> {
                UUID product = rs.getObject(1, UUID.class);
                UUID image = rs.getObject(2, UUID.class);
                images.computeIfAbsent(product, key -> new ArrayList<>()).add(new Image(image,
                        "/api/v1/hardware/catalog/products/" + product + "/images/" + image,
                        rs.getString(3), rs.getInt(4), rs.getInt(5), rs.getBoolean(6), true));
            }, arguments);
        var result = new HashMap<UUID, HardwareCatalogSourceResponse>();
        jdbc.query("SELECT * FROM hardware_product_sources WHERE quality='VERIFIED' AND product_id IN (" + slots + ")", rs -> {
            UUID id = rs.getObject("product_id", UUID.class);
            List<String> gtins = mapper.readValue(rs.getString("gtins"),
                    mapper.getTypeFactory().constructCollectionType(List.class, String.class));
            List<Group> groups = mapper.readValue(rs.getString("feature_groups"),
                    mapper.getTypeFactory().constructCollectionType(List.class, Group.class));
            Map<String, Value> typed = mapper.readValue(rs.getString("typed_specs"),
                    mapper.getTypeFactory().constructMapType(Map.class, String.class, Value.class));
            var updated = rs.getTimestamp("source_updated_at");
            result.put(id, new HardwareCatalogSourceResponse(rs.getString("source_provider"), rs.getString("external_product_id"),
                    rs.getString("brand"), rs.getString("mpn"), gtins, rs.getString("locale"),
                    updated == null ? null : updated.toInstant(), rs.getTimestamp("synced_at").toInstant(),
                    "VERIFIED", typed, groups, List.copyOf(images.getOrDefault(id, List.of()))));
        }, arguments);
        return result;
    }
    public void media(UUID sourceId, String assetId, com.ltz.hardware.application.port.CatalogMediaStorage.Stored stored,
            String alt, boolean primary) {
        jdbc.update("""
            INSERT INTO hardware_product_media(id,source_id,asset_id,storage_key,content_hash,media_type,width,height,alt,rights_verified,is_primary)
            VALUES (?,?,?,?,?,'image/png',?,?,?,true,?)
            ON CONFLICT(source_id,asset_id) DO UPDATE SET storage_key=excluded.storage_key,
                content_hash=excluded.content_hash,width=excluded.width,height=excluded.height,
                rights_verified=true,is_primary=excluded.is_primary,updated_at=now(),version=hardware_product_media.version+1
            """, UUID.randomUUID(), sourceId, assetId, stored.key(), stored.hash(), stored.width(), stored.height(), alt, primary);
    }
    public void clearMedia(UUID sourceId) { jdbc.update("DELETE FROM hardware_product_media WHERE source_id=?", sourceId); }
    public String mediaKey(UUID productId, UUID imageId) {
        var rows = jdbc.queryForList("""
            SELECT m.storage_key FROM hardware_product_media m JOIN hardware_product_sources s ON s.id=m.source_id
            JOIN hardware_products p ON p.id=s.product_id
            WHERE s.product_id=? AND m.id=? AND s.quality='VERIFIED' AND m.rights_verified
            AND p.status='ACTIVE' AND p.catalog_published
            """, String.class, productId, imageId);
        return rows.isEmpty() ? null : rows.getFirst();
    }
}
