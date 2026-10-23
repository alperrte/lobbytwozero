package com.ltz.hardware.infrastructure.messaging.outbox;

import com.ltz.hardware.domain.event.HardwareReviewEvent;
import com.ltz.hardware.domain.exception.HardwareDeliveryUnavailableException;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.support.SqlArrayValue;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.ObjectMapper;

@Repository
public class HardwareModerationOutbox {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    public HardwareModerationOutbox(JdbcTemplate jdbc, ObjectMapper mapper) { this.jdbc = jdbc; this.mapper = mapper; }

    /** Caller transaction owns both the business mutation and this bounded PENDING event. */
    public void append(HardwareReviewEvent<?> event) {
        var payload = mapper.writeValueAsString(event);
        if (payload.length() > 4096) throw new IllegalArgumentException("Olay boyutu sınırı aşıldı");
        jdbc.queryForList("SELECT pg_advisory_xact_lock(hashtextextended('hardware-moderation-outbox',17))");
        Long pending = jdbc.queryForObject("SELECT count(*) FROM outbox_events WHERE status IN ('PENDING','FAILED')", Long.class);
        if (pending != null && pending >= 10000) throw new HardwareDeliveryUnavailableException();
        jdbc.update("""
            INSERT INTO outbox_events(id,aggregate_type,aggregate_id,event_type,event_version,payload,occurred_at)
            VALUES (?,'HARDWARE_REVIEW',?,?,?,CAST(? AS jsonb),?)
            """, event.eventId(), event.aggregateId(), event.eventType(), event.eventVersion(), payload,
                java.sql.Timestamp.from(event.occurredAt()));
    }

    public Map<UUID, String> statuses(Collection<UUID> ids) {
        if (ids.isEmpty()) return Map.of();
        var result = new HashMap<UUID, String>();
        RowCallbackHandler collectStatus = row -> result.put(row.getObject("id", UUID.class), row.getString("status"));
        jdbc.query("SELECT id,status FROM outbox_events WHERE id=ANY(?::uuid[])", collectStatus,
                new SqlArrayValue("uuid", ids.toArray()));
        return result;
    }
}
