package com.ltz.hardware.infrastructure.persistence.repository;

import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.SqlArrayValue;
import org.springframework.stereotype.Repository;

@Repository
public class HardwareBatchQuery {
    public record Window(UUID profileId, int page, int size) { }
    private final JdbcTemplate jdbc;

    public HardwareBatchQuery(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public Map<UUID, List<UUID>> componentIds(List<Window> windows) {
        var sql = """
            SELECT requested.profile_id, selected.id
            FROM unnest(?::uuid[], ?::bigint[], ?::integer[]) WITH ORDINALITY
                AS requested(profile_id, row_offset, row_limit, ordinal)
            CROSS JOIN LATERAL (
                SELECT c.id, c.created_at FROM hardware_profile_components c
                WHERE c.profile_id = requested.profile_id
                ORDER BY c.created_at, c.id LIMIT requested.row_limit OFFSET requested.row_offset
            ) selected
            ORDER BY requested.ordinal, selected.created_at, selected.id
            """;
        var profileIds = new SqlArrayValue("uuid", windows.stream().map(Window::profileId).toArray());
        var offsets = new SqlArrayValue("bigint", windows.stream().map(w -> (long) w.page() * w.size()).toArray());
        var limits = new SqlArrayValue("integer", windows.stream().map(Window::size).toArray());
        Map<UUID, List<UUID>> result = new LinkedHashMap<>();
        jdbc.query(sql, row -> {
            var profileId = row.getObject("profile_id", UUID.class);
            result.computeIfAbsent(profileId, ignored -> new ArrayList<>()).add(row.getObject("id", UUID.class));
        }, profileIds, offsets, limits);
        return result;
    }
}
