package com.ltz.performance.application.service;

import com.ltz.performance.api.dto.response.PerformanceEvidenceResponse;
import com.ltz.performance.domain.entity.MeasurementContext;
import com.ltz.performance.infrastructure.persistence.repository.PerformanceAccountAccessGuard;
import com.ltz.performance.infrastructure.persistence.repository.PerformanceEvidenceRepository;
import com.ltz.performance.infrastructure.persistence.repository.PerformanceEvidenceRepository.Distribution;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PerformanceEvidenceService {
    public static final String METHOD = "SESSION_MEDIAN_P10_P90_IQR_1_5_V1";
    private final PerformanceEvidenceRepository repository;
    private final PerformanceAccountAccessGuard guard;
    private final Clock clock;

    public PerformanceEvidenceService(PerformanceEvidenceRepository repository, PerformanceAccountAccessGuard guard, Clock clock) {
        this.repository = repository;
        this.guard = guard;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PerformanceEvidenceResponse evidence(UUID owner, UUID game, UUID profile, MeasurementContext context) {
        guard.requireActive(owner);
        var now = clock.instant();
        var all = repository.distributions(owner, game, profile, context, now.minus(Duration.ofDays(30)), now);
        var considered = all.stream().limit(20).toList();
        var eligible = considered.stream().filter(PerformanceEvidenceService::eligible).toList();
        long observed = considered.stream().mapToLong(Distribution::rawCount).sum();
        long used = eligible.stream().mapToLong(Distribution::inlierCount).sum();
        boolean sufficient = eligible.size() >= 3;
        return new PerformanceEvidenceResponse("fps-evidence-v1", game, profile, context.hardwareProfileVersion(),
                context.resolution(), context.graphicsPreset(), context.renderingMode(),
                sufficient ? "SUFFICIENT" : "INSUFFICIENT_DATA", "AUTHENTICATED_DEVICE", "OWNER_SNAPSHOT_DEVICE_DECLARATION",
                METHOD, considered.size(), eligible.size(), observed, used, observed - used,
                sufficient ? median(eligible.stream().map(Distribution::p10).toList()) : null,
                sufficient ? median(eligible.stream().map(Distribution::p90).toList()) : null,
                eligible.stream().map(Distribution::firstAt).min(java.time.Instant::compareTo).orElse(null),
                eligible.stream().map(Distribution::lastAt).max(java.time.Instant::compareTo).orElse(null),
                all.size() > 20, "LOW", List.of("DEVICE_CONTEXT_NOT_ATTESTED", "GAME_BUILD_AND_BACKGROUND_LOAD_UNCONTROLLED",
                        "HISTORICAL_RANGE_NOT_CURRENT_MEASUREMENT", "MAX_20_SESSIONS_600_SAMPLES_30_DAYS"));
    }

    private static boolean eligible(Distribution row) {
        return row.inlierCount() >= 60 && row.firstAt() != null && row.lastAt() != null
                && Duration.between(row.firstAt(), row.lastAt()).compareTo(Duration.ofSeconds(60)) >= 0;
    }

    private static double median(List<Double> values) {
        var sorted = values.stream().sorted().toList();
        int middle = sorted.size() / 2;
        return sorted.size() % 2 == 0 ? (sorted.get(middle - 1) + sorted.get(middle)) / 2 : sorted.get(middle);
    }
}
