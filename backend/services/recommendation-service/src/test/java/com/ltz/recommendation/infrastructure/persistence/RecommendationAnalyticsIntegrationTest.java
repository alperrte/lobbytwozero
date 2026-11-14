package com.ltz.recommendation.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ltz.recommendation.application.port.RecommendationAnalytics.Observation;
import com.ltz.recommendation.application.service.RecommendationExecutionService;
import com.ltz.recommendation.application.port.RecommendationAlgorithm.Input;
import com.ltz.recommendation.domain.enums.RecommendationOutcome;
import com.ltz.recommendation.infrastructure.persistence.repository.PostgresRecommendationAnalytics;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;

@SpringBootTest
@ContextConfiguration(initializers = DisposableDatabaseInitializer.class)
class RecommendationAnalyticsIntegrationTest {
    @Autowired private PostgresRecommendationAnalytics analytics;
    @Autowired private DataSource dataSource;
    @Autowired private EntityManager entityManager;
    @Autowired private RecommendationExecutionService executions;
    private JdbcTemplate jdbc;

    @BeforeEach
    void resetOnlyDisposableTestTable() {
        jdbc = new JdbcTemplate(dataSource);
        jdbc.update("DELETE FROM recommendation_observations");
    }

    @Test
    void flywayAndJpaValidateRealSchemaWithoutPersonalDataColumns() {
        assertThat(jdbc.queryForObject("SELECT count(*) FROM flyway_schema_history WHERE version = '11' AND success", Integer.class))
                .isEqualTo(1);
        assertThat(entityManager.getMetamodel().getEntities()).hasSize(1);
        assertThat(jdbc.queryForList("SELECT column_name FROM information_schema.columns "
                + "WHERE table_name = 'recommendation_observations'", String.class)).containsExactlyInAnyOrder(
                        "id", "algorithm_version", "occurred_at", "outcome", "candidate_count", "result_count", "total_score", "version");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM pg_indexes WHERE indexname = 'ix_recommendation_observations_occurred_at'",
                Integer.class)).isEqualTo(1);
    }

    @Test
    void realExecutionRecordsExactlyOneEmptyOutcomeAndAggregate() {
        var result = executions.execute(new Input("rules-v1", List.of(), 10));
        var totals = analytics.totals(Instant.now().minusSeconds(60), Instant.now().plusSeconds(1));
        assertThat(totals.getExecutions()).isEqualTo(1);
        assertThat(totals.getEmpty()).isEqualTo(1);
        assertThat(totals.getUnavailable()).isZero();
        assertThat(totals.getResults()).isZero();
        assertThat(totals.getScore()).isZero();
        assertThat(jdbc.queryForObject("SELECT id FROM recommendation_observations", UUID.class)).isEqualTo(result.runId());
    }

    @Test
    void duplicateAndConcurrentDeliveryCountOnce() throws Exception {
        Observation observation = generated();
        try (var executor = Executors.newFixedThreadPool(4)) {
            List<Callable<Void>> tasks = new ArrayList<>();
            for (int index = 0; index < 12; index++) tasks.add(() -> { analytics.record(observation); return null; });
            for (var result : executor.invokeAll(tasks)) result.get();
        }
        analytics.record(observation);
        var totals = analytics.totals(Instant.now().minusSeconds(60), Instant.now().plusSeconds(1));
        assertThat(totals.getExecutions()).isEqualTo(1);
        assertThat(totals.getResults()).isEqualTo(2);
        assertThat(totals.getScore()).isEqualTo(5000);
    }

    @Test
    void duplicateWithChangedPayloadIsRejectedWithoutChangingMetrics() {
        Observation observation = generated();
        analytics.record(observation);
        Observation conflict = new Observation(observation.runId(), "rules-v2", observation.occurredAt(),
                RecommendationOutcome.GENERATED, 3, 2, 5000);
        assertThatThrownBy(() -> analytics.record(conflict)).isInstanceOf(IllegalStateException.class);
        assertThat(jdbc.queryForObject("SELECT algorithm_version FROM recommendation_observations", String.class))
                .isEqualTo("rules-v1");
    }

    @Test
    void databaseEnforcesBoundsStatusAndPrimaryKey() {
        Observation observation = generated();
        analytics.record(observation);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO recommendation_observations SELECT * FROM recommendation_observations"))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        for (String mutation : List.of("candidate_count = 501", "result_count = 51", "total_score = -1",
                "outcome = 'CLICK'", "algorithm_version = 'private user data'", "outcome = 'EMPTY'")) {
            assertThatThrownBy(() -> jdbc.update("UPDATE recommendation_observations SET " + mutation))
                    .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        }
    }

    @Test
    void retentionRejectsOldOrFutureEventsAndPurgesOnlyExpiredRows() {
        analytics.record(generated());
        jdbc.update("INSERT INTO recommendation_observations (id, algorithm_version, occurred_at, outcome, "
                + "candidate_count, result_count, total_score) VALUES (?, 'rules-v1', now() - interval '31 days', 'EMPTY', 0, 0, 0)",
                UUID.randomUUID());
        analytics.purgeExpired();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM recommendation_observations", Integer.class)).isEqualTo(1);
        for (Instant time : List.of(Instant.now().minus(31, ChronoUnit.DAYS), Instant.now().plusSeconds(120))) {
            assertThatThrownBy(() -> analytics.record(new Observation(UUID.randomUUID(), "rules-v1", time,
                    RecommendationOutcome.EMPTY, 0, 0, 0))).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void expensiveOrReversedAnalyticsIntervalsAreRejected() {
        Instant now = Instant.now();
        assertThatThrownBy(() -> analytics.totals(now, now)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> analytics.totals(now.minus(31, ChronoUnit.DAYS), now))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> analytics.totals(now, now.plusSeconds(120))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> analytics.totals(now.minus(30, ChronoUnit.DAYS).plusSeconds(1), now.plusSeconds(5)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static Observation generated() {
        return new Observation(UUID.randomUUID(), "rules-v1", Instant.now(), RecommendationOutcome.GENERATED, 3, 2, 5000);
    }
}
