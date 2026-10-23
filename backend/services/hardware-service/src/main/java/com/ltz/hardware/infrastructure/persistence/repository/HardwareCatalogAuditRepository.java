package com.ltz.hardware.infrastructure.persistence.repository;

import com.ltz.hardware.domain.entity.CatalogText;
import com.ltz.hardware.domain.exception.HardwareRateLimitException;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Must be called inside the mutation transaction; there is intentionally no update/delete API. */
@Repository
public class HardwareCatalogAuditRepository {
    private final JdbcTemplate jdbc;

    public HardwareCatalogAuditRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public void append(UUID actorId, String action, UUID targetId, String reason) {
        String safeReason = CatalogText.clean(reason, 500);
        // Serialize only this actor's writes across service instances.
        jdbc.queryForList("SELECT pg_advisory_xact_lock(hashtextextended(CAST(? AS text), 0))", actorId.toString());
        Long recent = jdbc.queryForObject("""
                SELECT count(*) FROM hardware_catalog_audit
                WHERE actor_id = ? AND created_at > now() - interval '1 minute'
                """, Long.class, actorId);
        if (recent != null && recent >= 60) throw new HardwareRateLimitException();
        jdbc.update("INSERT INTO hardware_catalog_audit(id,actor_id,action,target_id,reason) VALUES (?,?,?,?,?)",
                UUID.randomUUID(), actorId, action, targetId, safeReason);
    }
}
