package com.ltz.library.infrastructure.persistence.repository;

import com.ltz.library.application.port.GameSummary;
import com.ltz.library.domain.entity.GameSnapshot;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class GameSnapshotRepository {
    private final JdbcTemplate jdbc;
    public GameSnapshotRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public Map<UUID, GameSnapshot> find(List<UUID> ids) {
        if (ids.isEmpty()) return Map.of();
        return jdbc.query(connection -> {
            var statement = connection.prepareStatement("SELECT game_id, title, cover_url, origin, metadata_completeness, game_type, status, refreshed_at, version FROM game_snapshots WHERE game_id = ANY (?)");
            statement.setArray(1, connection.createArrayOf("uuid", ids.toArray()));
            return statement;
        }, (rs, row) ->
                new GameSnapshot(rs.getObject("game_id", UUID.class), rs.getString("title"), rs.getString("cover_url"),
                        rs.getString("origin"), rs.getString("metadata_completeness"), rs.getString("game_type"),
                        rs.getString("status"), rs.getTimestamp("refreshed_at").toInstant(), rs.getLong("version")))
                .stream().collect(Collectors.toMap(GameSnapshot::getGameId, Function.identity()));
    }

    public void remember(List<GameSummary> cards, Instant observedAt) {
        var rows = cards.stream().filter(card -> card.title() != null).sorted(java.util.Comparator.comparing(card -> card.gameId().toString())).toList();
        if (rows.isEmpty()) return;
        jdbc.batchUpdate("""
                INSERT INTO game_snapshots (game_id,title,cover_url,origin,metadata_completeness,game_type,refreshed_at)
                VALUES (?,?,?,?,?,?,?)
                ON CONFLICT (game_id) DO UPDATE SET title=EXCLUDED.title,cover_url=EXCLUDED.cover_url,
                    origin=EXCLUDED.origin,metadata_completeness=EXCLUDED.metadata_completeness,
                    game_type=EXCLUDED.game_type,refreshed_at=EXCLUDED.refreshed_at,version=game_snapshots.version+1
                WHERE game_snapshots.refreshed_at < EXCLUDED.refreshed_at
                """, rows, 50, (statement, card) -> {
                    statement.setObject(1, card.gameId()); statement.setString(2, card.title());
                    statement.setString(3, card.coverUrl()); statement.setString(4, card.origin());
                    statement.setString(5, card.metadataCompleteness()); statement.setString(6, card.gameType());
                    statement.setTimestamp(7, Timestamp.from(observedAt));
                });
    }
}
