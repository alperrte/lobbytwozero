package com.ltz.recommendation.application.service;

import com.ltz.recommendation.application.port.GameMetadataSource;
import com.ltz.recommendation.application.port.HardwareAssessmentSource;
import com.ltz.recommendation.application.port.PersonalizationSource;
import com.ltz.recommendation.application.port.PersonalizationSource.Actor;
import com.ltz.recommendation.application.port.PersonalizationSource.Filters;
import com.ltz.recommendation.application.port.PersonalizationSource.Match;
import com.ltz.recommendation.application.port.RecommendationHistory;
import com.ltz.recommendation.application.service.HardwareRequirementEvaluator.Status;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * REC-06/16: kullanıcının zevk adayları içinde, sahibi olduğu donanım profiliyle MİNİMUM gereksinimi KANITLI olarak
 * karşılanamayanları eler. Marka/model sıralaması uydurulmaz: yalnız MEETS (kanıtlı) ve UNKNOWN (bilinen uyumsuzluk yok)
 * döner; BELOW olanlar listeden çıkar. Sonuç saklanmaz (stateless).
 */
@Service
public class HardwareCompatibleGameService {
    public static final String ALGORITHM_VERSION = "hardware-compatible-v1";
    public static final int DEFAULT_SIZE = 10;
    public static final int MAX_SIZE = 20;
    static final int CANDIDATE_POOL = 50;
    static final String MEETS = "MEETS_MINIMUM_COMPUTE";

    public record Item(UUID gameId, int affinity, List<String> matchedGenres, String compatibility, String reason) { }
    public record Result(String algorithmVersion, Instant createdAt, UUID hardwareProfileId, String platform,
            List<Item> items, int excludedBelowMinimum, List<String> limitations) { }

    private final PersonalizationSource personalization;
    private final HardwareAssessmentSource hardware;
    private final GameMetadataSource games;
    private final HardwareRequirementEvaluator evaluator;
    private final PersonalizedRecommendationService consent;
    private final RecommendationHistory history;
    private final RecommendationRateLimiter limits;
    private final Clock clock;

    @Autowired
    public HardwareCompatibleGameService(PersonalizationSource personalization, HardwareAssessmentSource hardware,
            GameMetadataSource games, HardwareRequirementEvaluator evaluator, PersonalizedRecommendationService consent,
            RecommendationHistory history, RecommendationRateLimiter limits) {
        this(personalization, hardware, games, evaluator, consent, history, limits, Clock.systemUTC());
    }

    HardwareCompatibleGameService(PersonalizationSource personalization, HardwareAssessmentSource hardware,
            GameMetadataSource games, HardwareRequirementEvaluator evaluator, PersonalizedRecommendationService consent,
            RecommendationHistory history, RecommendationRateLimiter limits, Clock clock) {
        this.personalization = personalization;
        this.hardware = hardware;
        this.games = games;
        this.evaluator = evaluator;
        this.consent = consent;
        this.history = history;
        this.limits = limits;
        this.clock = clock;
    }

    public Result recommend(Actor actor, UUID profileId, String platform, String genre, Integer size) {
        int limit = size == null ? DEFAULT_SIZE : size;
        if (profileId == null || platform == null || limit < 1 || limit > MAX_SIZE || invalid(platform) || invalid(genre)) {
            throw new IllegalArgumentException("Invalid filters");
        }
        limits.acquire(actor.accountId(), true);
        consent.verifyConsent(actor);
        var profile = hardware.profile(actor, profileId);
        var excluded = history.notInterested(actor.accountId());
        List<Match> matches = personalization.candidates(actor, new Filters(genre, null, CANDIDATE_POOL)).stream()
                .filter(match -> !excluded.contains(match.gameId())).toList();
        List<String> limitations = new ArrayList<>(List.of("DECLARED_TECHNICAL_DATA_ONLY", "OS_DRIVERS_AND_FREE_SPACE_NOT_VERIFIED",
                "NO_CPU_GPU_MODEL_PERFORMANCE_ORDER", "UNKNOWN_IS_NOT_KNOWN_INCOMPATIBLE"));
        if (matches.isEmpty()) return result(profileId, platform, List.of(), 0, limitations);
        var batch = games.requirements(actor, matches.stream().map(Match::gameId).toList(), platform);
        Scan scan = scan(matches, batch, profile);
        if (!batch.unavailable().isEmpty()) limitations.add("SOME_CANDIDATES_UNAVAILABLE");
        if (scan.missingRequirements) limitations.add("SOME_REQUIREMENTS_MISSING");
        scan.items.sort(Comparator.comparing((Item item) -> !MEETS.equals(item.compatibility()))
                .thenComparing(Comparator.comparingInt(Item::affinity).reversed())
                .thenComparing(item -> item.gameId().toString()));
        return result(profileId, platform, scan.items.stream().limit(limit).toList(), scan.below, limitations);
    }

    private static final class Scan {
        private final List<Item> items = new ArrayList<>();
        private int below;
        private boolean missingRequirements;
    }

    private Scan scan(List<Match> matches, GameMetadataSource.Batch batch, HardwareAssessmentSource.Profile profile) {
        Scan scan = new Scan();
        for (Match match : matches) {
            var requirements = batch.byGame().get(match.gameId());
            // kaynakta görünmüyor (kaldırılmış/yetişkin/bilinmiyor): sessizce düşer
            if (requirements != null) add(scan, match, requirements, profile);
        }
        return scan;
    }

    private void add(Scan scan, Match match, List<HardwareAssessmentSource.Requirement> requirements,
            HardwareAssessmentSource.Profile profile) {
        var minimum = requirements.stream().filter(item -> "MINIMUM".equals(item.level())).findFirst().orElse(null);
        var level = evaluator.evaluate(profile, minimum, "MINIMUM");
        if (level.overallStatus() == Status.BELOW) {
            scan.below++;
        } else {
            scan.missingRequirements |= minimum == null;
            scan.items.add(item(match, minimum == null, level.computeStatus() == Status.MEETS));
        }
    }

    private static Item item(Match match, boolean noMinimum, boolean meets) {
        String reason = meets ? "MINIMUM_CPU_GPU_RAM_PROVEN" : noMinimum ? "REQUIREMENTS_MISSING" : "MINIMUM_NOT_PROVEN";
        return new Item(match.gameId(), match.affinity(), match.matchedGenres(), meets ? MEETS : "UNKNOWN", reason);
    }

    private Result result(UUID profileId, String platform, List<Item> items, int below, List<String> limitations) {
        return new Result(ALGORITHM_VERSION, clock.instant(), profileId, platform, items, below, List.copyOf(limitations));
    }

    private static boolean invalid(String value) {
        return value != null && (value.isBlank() || value.length() > 64 || value.chars().anyMatch(Character::isISOControl));
    }
}
