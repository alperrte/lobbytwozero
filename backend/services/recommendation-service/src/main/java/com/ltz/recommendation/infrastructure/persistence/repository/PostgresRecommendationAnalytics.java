package com.ltz.recommendation.infrastructure.persistence.repository;

import com.ltz.recommendation.application.port.RecommendationAnalytics;
import com.ltz.recommendation.domain.entity.RecommendationObservation;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@EnableScheduling
public class PostgresRecommendationAnalytics implements RecommendationAnalytics {
    private final RecommendationObservationRepository repository;

    public PostgresRecommendationAnalytics(RecommendationObservationRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public void record(Observation observation) {
        Instant now = Instant.now();
        if (observation.occurredAt().isBefore(now.minus(30, ChronoUnit.DAYS))
                || observation.occurredAt().isAfter(now.plusSeconds(30))) {
            throw new IllegalArgumentException("Observation outside retention window");
        }
        if (repository.insert(observation.runId(), observation.algorithmVersion(), observation.occurredAt(),
                observation.outcome().name(), observation.candidateCount(), observation.resultCount(),
                observation.totalScore()) == 0) {
            RecommendationObservation existing = repository.findById(observation.runId()).orElseThrow();
            if (!same(existing, observation)) {
                throw new IllegalStateException("Conflicting observation");
            }
        }
    }

    @Transactional(readOnly = true)
    public RecommendationObservationRepository.Totals totals(Instant since, Instant until) {
        Instant now = Instant.now();
        if (!since.isBefore(until) || since.isBefore(now.minus(30, ChronoUnit.DAYS))
                || until.isAfter(now.plusSeconds(30))
                || Duration.between(since, until).compareTo(Duration.ofDays(30)) > 0) {
            throw new IllegalArgumentException("Invalid analytics interval");
        }
        return repository.totals(since, until);
    }

    @Scheduled(fixedDelay = 60000)
    @Transactional
    public void purgeExpired() {
        repository.deleteExpiredBatch(Instant.now().minus(30, ChronoUnit.DAYS));
    }

    private static boolean same(RecommendationObservation existing, Observation observation) {
        return existing.getAlgorithmVersion().equals(observation.algorithmVersion())
                && existing.getOccurredAt().equals(observation.occurredAt().truncatedTo(ChronoUnit.MICROS))
                && existing.getOutcome().name().equals(observation.outcome().name())
                && existing.getCandidateCount() == observation.candidateCount()
                && existing.getResultCount() == observation.resultCount()
                && existing.getTotalScore() == observation.totalScore();
    }
}
