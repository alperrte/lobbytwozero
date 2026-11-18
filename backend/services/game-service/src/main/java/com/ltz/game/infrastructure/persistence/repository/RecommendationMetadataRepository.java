package com.ltz.game.infrastructure.persistence.repository;

import com.ltz.game.api.dto.response.RecommendationMetadataBatchResponse.Item;
import com.ltz.game.api.dto.response.RecommendationMetadataBatchResponse.Requirement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** REC-06/16 toplu metadata: sabit 3 SQL (oyun, terimler, gereksinimler); oyun başına sorgu yoktur. */
@Repository @RequiredArgsConstructor
public class RecommendationMetadataRepository {
    // Keşif listeleriyle aynı görünürlük: ACTIVE, mağazadan kaldırılmamış, yetişkin işareti true olmayan.
    private static final String GAMES = """
            select g.id, g.game_type from games g
            where g.id in (:ids) and g.status='ACTIVE' and g.delisted_at is null and g.adult is not true
            """;
    private static final String TERMS = """
            select gt.game_id, t.kind, t.code from game_terms gt join catalog_terms t on t.id=gt.term_id
            where gt.game_id in (:ids) and t.kind in ('GENRE','PLATFORM') order by t.kind, t.code
            """;
    private static final String REQUIREMENTS = """
            select game_id, level, platform, os, cpu, gpu, ram_mb, storage_mb from game_system_requirements
            where game_id in (:ids) order by platform, level
            """;
    private final NamedParameterJdbcTemplate jdbc;

    public Map<UUID, Item> visible(Collection<UUID> ids) {
        if (ids.isEmpty()) return Map.of();
        var params = Map.of("ids", List.copyOf(ids));
        Map<UUID, String> types = new LinkedHashMap<>();
        jdbc.query(GAMES, params, (RowCallbackHandler) rs -> types.put(rs.getObject("id", UUID.class), rs.getString("game_type")));
        if (types.isEmpty()) return Map.of();
        Map<UUID, List<String>> genres = new LinkedHashMap<>();
        Map<UUID, List<String>> platforms = new LinkedHashMap<>();
        jdbc.query(TERMS, params, (RowCallbackHandler) rs -> {
            var target = "GENRE".equals(rs.getString("kind")) ? genres : platforms;
            target.computeIfAbsent(rs.getObject("game_id", UUID.class), key -> new ArrayList<>()).add(rs.getString("code"));
        });
        Map<UUID, List<Requirement>> requirements = new LinkedHashMap<>();
        jdbc.query(REQUIREMENTS, params, (RowCallbackHandler) rs -> requirements
                .computeIfAbsent(rs.getObject("game_id", UUID.class), key -> new ArrayList<>())
                .add(new Requirement(rs.getString("level"), rs.getString("platform"), rs.getString("os"), rs.getString("cpu"),
                        rs.getString("gpu"), rs.getObject("ram_mb", Integer.class), rs.getObject("storage_mb", Integer.class))));
        Map<UUID, Item> items = new LinkedHashMap<>();
        types.forEach((id, type) -> items.put(id, new Item(id, type, List.copyOf(genres.getOrDefault(id, List.of())),
                List.copyOf(platforms.getOrDefault(id, List.of())), List.copyOf(requirements.getOrDefault(id, List.of())))));
        return items;
    }
}
