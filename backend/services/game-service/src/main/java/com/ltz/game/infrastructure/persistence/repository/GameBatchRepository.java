package com.ltz.game.infrastructure.persistence.repository;

import com.ltz.game.api.dto.response.GameCardResponse;
import com.ltz.game.domain.enums.GameOrigin;
import com.ltz.game.domain.enums.GameType;
import com.ltz.game.domain.enums.MetadataCompleteness;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** Çoklu kart okuması: sabit sayıda SQL (oyunlar + dış referanslar), oyun başına sorgu yoktur. */
@Repository @RequiredArgsConstructor
public class GameBatchRepository {
    private static final String GAMES = """
            select g.id, g.title, g.origin, g.metadata_completeness, g.game_type,
                (select m.url from game_media m where m.game_id=g.id and m.type='COVER'
                    order by m.sort_order,m.id limit 1) as cover_url
            from games g where g.status='ACTIVE' and g.id in (:ids)
            """;
    private static final String REFERENCES = """
            select game_id,provider,external_id,last_synced_at from game_external_refs
            where game_id in (:ids) order by provider,external_id
            """;
    private final NamedParameterJdbcTemplate jdbc;

    public Map<UUID, GameCardResponse> cards(Collection<UUID> ids) {
        if (ids.isEmpty()) return Map.of();
        var params = Map.of("ids", List.copyOf(ids));
        Map<UUID, List<GameCardResponse.ExternalReference>> references = new HashMap<>();
        jdbc.query(REFERENCES, params, (RowCallbackHandler) rs -> references
                .computeIfAbsent(rs.getObject("game_id", UUID.class), key -> new ArrayList<>())
                .add(new GameCardResponse.ExternalReference(rs.getString("provider"), rs.getString("external_id"), instant(rs))));
        Map<UUID, GameCardResponse> cards = new HashMap<>();
        jdbc.query(GAMES, params, (RowCallbackHandler) rs -> {
            UUID id = rs.getObject("id", UUID.class);
            cards.put(id, new GameCardResponse(id, rs.getString("title"), GameOrigin.valueOf(rs.getString("origin")),
                    MetadataCompleteness.valueOf(rs.getString("metadata_completeness")), GameType.valueOf(rs.getString("game_type")),
                    List.copyOf(references.getOrDefault(id, List.of())), rs.getString("cover_url")));
        });
        return cards;
    }

    private static Instant instant(ResultSet rs) throws SQLException {
        var timestamp = rs.getTimestamp("last_synced_at");
        return timestamp == null ? null : timestamp.toInstant();
    }
}
