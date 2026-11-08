package com.ltz.review.infrastructure.persistence.repository;

import com.ltz.review.api.dto.response.GameRatingSummaryResponse;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class RatingSummaryRepository {
    private final JdbcClient jdbc;

    public RatingSummaryRepository(JdbcClient jdbc) { this.jdbc = jdbc; }

    public GameRatingSummaryResponse find(UUID gameId) {
        return findAll(List.of(gameId)).getFirst();
    }

    private static final String PAGE_SELECT = "SELECT game_id, round(avg(rating),2) AS average_rating, count(*) AS rating_count FROM game_ratings ";
    private static final String PAGE_TAIL = "GROUP BY game_id ORDER BY game_id LIMIT :limit";
    private static final String PAGE_FIRST = PAGE_SELECT + PAGE_TAIL;
    private static final String PAGE_AFTER = PAGE_SELECT + "WHERE game_id > :after " + PAGE_TAIL;

    /** En az bir puanı olan oyunlar, game_id artan; after dışlayıcı imleçtir. Tek SQL, tek snapshot. */
    public List<GameRatingSummaryResponse> findPage(UUID after, int limit) {
        var statement = jdbc.sql(after == null ? PAGE_FIRST : PAGE_AFTER).param("limit", limit);
        if (after != null) statement = statement.param("after", after);
        return statement.query((rs, row) -> new GameRatingSummaryResponse(
                rs.getObject("game_id", UUID.class), rs.getBigDecimal("average_rating"), rs.getLong("rating_count"))).list();
    }

    public List<GameRatingSummaryResponse> findAll(List<UUID> gameIds) {
        if (gameIds.isEmpty()) return List.of();
        Map<UUID, GameRatingSummaryResponse> found = jdbc.sql("""
                SELECT game_id, round(avg(rating),2) AS average_rating, count(*) AS rating_count
                FROM game_ratings WHERE game_id IN (:games) GROUP BY game_id
                """).param("games", gameIds).query((rs, row) -> new GameRatingSummaryResponse(
                        rs.getObject("game_id", UUID.class), rs.getBigDecimal("average_rating"), rs.getLong("rating_count")))
                .list().stream().collect(Collectors.toMap(GameRatingSummaryResponse::gameId, Function.identity()));
        return gameIds.stream().map(game -> found.getOrDefault(game, new GameRatingSummaryResponse(game, null, 0))).toList();
    }
}
