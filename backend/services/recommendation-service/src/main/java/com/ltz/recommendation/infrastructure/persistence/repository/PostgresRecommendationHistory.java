package com.ltz.recommendation.infrastructure.persistence.repository;

import com.ltz.recommendation.application.port.PersonalizationSource.Filters;
import com.ltz.recommendation.application.port.RecommendationHistory;
import com.ltz.recommendation.domain.exception.RecommendationRequestException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class PostgresRecommendationHistory implements RecommendationHistory {
    private final JdbcTemplate jdbc;
    public PostgresRecommendationHistory(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    public void lockAccount(UUID accountId) {
        jdbc.queryForList("SELECT pg_advisory_xact_lock(hashtextextended(?, 0))", accountId.toString());
    }

    @Override
    public Run save(UUID owner, Run run) {
        Long count = jdbc.queryForObject("SELECT count(*) FROM recommendation_runs WHERE user_id = ? AND expires_at > now()",
                Long.class, owner);
        if (count != null && count >= 500) throw new RecommendationRequestException(429, "HISTORY_LIMIT_REACHED");
        jdbc.update("""
                INSERT INTO recommendation_runs(id,user_id,algorithm_version,genre,platform,result_limit,created_at,expires_at)
                VALUES (?,?,?,?,?,?,?,?)
                """, run.runId(), owner, run.algorithmVersion(), run.filters().genre(), run.filters().platform(),
                run.filters().size(), Timestamp.from(run.createdAt()), Timestamp.from(run.createdAt().plus(30, ChronoUnit.DAYS)));
        for (StoredItem item : run.items()) {
            jdbc.update(connection -> {
                var statement = connection.prepareStatement("""
                        INSERT INTO recommendation_items(id,run_id,game_id,score,rank,matched_genres) VALUES (?,?,?,?,?,?)
                        """);
                statement.setObject(1, item.itemId());
                statement.setObject(2, run.runId());
                statement.setObject(3, item.gameId());
                statement.setInt(4, item.score());
                statement.setInt(5, item.rank());
                statement.setArray(6, connection.createArrayOf("varchar", item.matchedGenres().toArray()));
                return statement;
            });
        }
        return run;
    }

    @Override
    @Transactional(readOnly = true, isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public Run read(UUID owner, UUID runId) {
        var runs = jdbc.query("SELECT * FROM recommendation_runs WHERE id=? AND user_id=? AND expires_at>now()",
                (row, index) -> new Run(row.getObject("id", UUID.class), row.getString("algorithm_version"),
                        row.getTimestamp("created_at").toInstant(), new Filters(row.getString("genre"), row.getString("platform"),
                        row.getInt("result_limit")), List.of()), runId, owner);
        if (runs.isEmpty()) throw notFound();
        var run = runs.getFirst();
        var items = jdbc.query("SELECT * FROM recommendation_items WHERE run_id=? ORDER BY rank", (row, index) ->
                new StoredItem(row.getObject("id", UUID.class), row.getObject("game_id", UUID.class), row.getInt("score"),
                        row.getInt("rank"), Arrays.asList((String[]) row.getArray("matched_genres").getArray())), runId);
        return new Run(run.runId(), run.algorithmVersion(), run.createdAt(), run.filters(), List.copyOf(items));
    }

    @Override
    @Transactional(readOnly = true, isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public Page history(UUID owner, UUID beforeRunId, int size) {
        List<Summary> items;
        if (beforeRunId == null) {
            items = jdbc.query("""
                    SELECT r.*, (SELECT count(*) FROM recommendation_items i WHERE i.run_id=r.id) AS result_count
                    FROM recommendation_runs r WHERE user_id=? AND expires_at>now()
                    ORDER BY created_at DESC,id DESC LIMIT ?
                    """, PostgresRecommendationHistory::summary, owner, size + 1);
        } else {
            var cursor = read(owner, beforeRunId);
            items = jdbc.query("""
                    SELECT r.*, (SELECT count(*) FROM recommendation_items i WHERE i.run_id=r.id) AS result_count
                    FROM recommendation_runs r WHERE user_id=? AND expires_at>now() AND (created_at,id)<(?,?)
                    ORDER BY created_at DESC,id DESC LIMIT ?
                    """, PostgresRecommendationHistory::summary, owner, Timestamp.from(cursor.createdAt()), beforeRunId, size + 1);
        }
        boolean more = items.size() > size;
        List<Summary> page = List.copyOf(items.subList(0, Math.min(items.size(), size)));
        return new Page(page, more ? page.getLast().runId() : null);
    }

    @Override
    public Feedback feedback(UUID owner, UUID runId, UUID itemId, FeedbackValue value, long version) {
        Feedback previous = feedbackState(owner, runId, itemId);
        if (previous.value() == value) return previous;
        long actual = previous.version();
        if (version != actual) throw new RecommendationRequestException(409, "FEEDBACK_VERSION_CONFLICT");
        jdbc.update("""
                INSERT INTO recommendation_feedback(id,item_id,feedback,updated_at,version) VALUES (?,?,?,now(),?)
                ON CONFLICT (item_id) DO UPDATE SET feedback=EXCLUDED.feedback,updated_at=EXCLUDED.updated_at,version=EXCLUDED.version
                """, UUID.randomUUID(), itemId, value.name(), actual + 1);
        return new Feedback(itemId, value, actual + 1);
    }

    @Override
    @Transactional(readOnly = true, isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public Feedback feedbackState(UUID owner, UUID runId, UUID itemId) {
        var owned = jdbc.queryForObject("""
                SELECT count(*) FROM recommendation_items i JOIN recommendation_runs r ON r.id=i.run_id
                WHERE i.id=? AND r.id=? AND r.user_id=? AND r.expires_at>now()
                """, Integer.class, itemId, runId, owner);
        if (owned == null || owned != 1) throw notFound();
        var existing = jdbc.query("SELECT * FROM recommendation_feedback WHERE item_id=?",
                (row, index) -> new Feedback(itemId, FeedbackValue.valueOf(row.getString("feedback")), row.getLong("version")), itemId);
        return existing.isEmpty() ? new Feedback(itemId, null, 0) : existing.getFirst();
    }

    @Override
    public void erase(UUID owner) { jdbc.update("DELETE FROM recommendation_runs WHERE user_id=?", owner); }

    @Override
    public java.util.Set<UUID> notInterested(UUID owner) {
        return java.util.Set.copyOf(jdbc.queryForList("""
                SELECT DISTINCT i.game_id FROM recommendation_items i
                JOIN recommendation_runs r ON r.id=i.run_id JOIN recommendation_feedback f ON f.item_id=i.id
                WHERE r.user_id=? AND r.expires_at>now() AND f.feedback='NOT_INTERESTED'
                """, UUID.class, owner));
    }

    @Scheduled(fixedDelay = 60000)
    @Transactional
    public void purgeExpired() {
        jdbc.update("DELETE FROM recommendation_runs WHERE id IN "
                + "(SELECT id FROM recommendation_runs WHERE expires_at<=now() ORDER BY expires_at LIMIT 1000)");
    }

    private static Summary summary(ResultSet row, int index) throws SQLException {
        return new Summary(row.getObject("id", UUID.class), row.getString("algorithm_version"),
                row.getTimestamp("created_at").toInstant(), row.getInt("result_count"));
    }
    private static RecommendationRequestException notFound() { return new RecommendationRequestException(404, "RECOMMENDATION_NOT_FOUND"); }
}
