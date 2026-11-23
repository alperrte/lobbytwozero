package com.ltz.recommendation.application.service;

import com.ltz.recommendation.application.port.PersonalizationSource.Actor;
import com.ltz.recommendation.application.port.PlayerCandidateSource;
import com.ltz.recommendation.application.port.PlayerCandidateSource.Candidate;
import com.ltz.recommendation.application.port.PlayerCandidateSource.Taste;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * REC-13 (benzer oyuncular) ve REC-14 (arkadaş önerisi). Saklanmaz (stateless): aday havuzu, rıza/engel/arkadaşlık
 * süzgeçleri ve görünürlük User tarafında uygulanır; burada yalnız izinli tercih sinyalleri üzerinden deterministik
 * benzerlik hesaplanır. Üçüncü kişiye yalnız kimliği, skoru ve izleyiciyle ORTAK sinyaller döner.
 */
@Service
public class PlayerRecommendationService {
    public static final String ALGORITHM_VERSION = "player-affinity-v1";
    public static final int DEFAULT_SIZE = 10;
    public static final int MAX_SIZE = 20;

    /** Öneri türü: ağırlıklar (tür, platform, oyuncu tipi; toplam 100) ve zorunlu ortak sinyal kuralı. */
    public enum Kind {
        /** Benzer zevk: tür ağırlıklı; en az bir ortak tür yeter. */
        SIMILAR_PLAYERS(60, 20, 20, false),
        /** Arkadaş önerisi: birlikte oynanabilirlik için ortak tür VE ortak platform şart; platform eşit ağırlıklı. */
        FRIEND_SUGGESTIONS(40, 40, 20, true);

        final int genreWeight;
        final int platformWeight;
        final int typeWeight;
        final boolean requiresSharedPlatform;

        Kind(int genreWeight, int platformWeight, int typeWeight, boolean requiresSharedPlatform) {
            this.genreWeight = genreWeight;
            this.platformWeight = platformWeight;
            this.typeWeight = typeWeight;
            this.requiresSharedPlatform = requiresSharedPlatform;
        }
    }

    public record Item(UUID userId, int score, List<String> sharedGenres, List<String> sharedPlatforms,
            List<String> sharedPlayerTypes) {}

    public record Result(Kind kind, String algorithmVersion, Instant createdAt, List<Item> items, List<String> limitations) {}

    private final PlayerCandidateSource source;
    private final PersonalizedRecommendationService consent;
    private final RecommendationRateLimiter limits;
    private final Clock clock;

    @Autowired
    public PlayerRecommendationService(PlayerCandidateSource source, PersonalizedRecommendationService consent,
            RecommendationRateLimiter limits) {
        this(source, consent, limits, Clock.systemUTC());
    }

    PlayerRecommendationService(PlayerCandidateSource source, PersonalizedRecommendationService consent,
            RecommendationRateLimiter limits, Clock clock) {
        this.source = source;
        this.consent = consent;
        this.limits = limits;
        this.clock = clock;
    }

    public Result recommend(Actor actor, Kind kind, Integer size, String genre, String platform) {
        int limit = size == null ? DEFAULT_SIZE : size;
        if (limit < 1 || limit > MAX_SIZE || invalid(genre) || invalid(platform)) throw new IllegalArgumentException("Invalid filters");
        limits.acquire(actor.accountId(), true);
        consent.verifyConsent(actor);
        Taste viewer = source.viewer(actor);
        if (viewer.genres().isEmpty()) return result(kind, List.of(), List.of("VIEWER_PREFERENCES_MISSING"));
        var scan = source.candidates(actor, true, genre, platform);
        List<Item> items = new ArrayList<>();
        for (Candidate candidate : scan.candidates()) {
            Item item = score(kind, viewer, candidate);
            if (item != null) items.add(item);
        }
        items.sort(Comparator.comparingInt(Item::score).reversed().thenComparing(item -> item.userId().toString()));
        List<String> limitations = scan.truncated() ? List.of("CANDIDATE_SCAN_TRUNCATED") : List.of();
        return result(kind, items.stream().limit(limit).toList(), limitations);
    }

    private Result result(Kind kind, List<Item> items, List<String> limitations) {
        return new Result(kind, ALGORITHM_VERSION, clock.instant(), items, limitations);
    }

    static Item score(Kind kind, Taste viewer, Candidate candidate) {
        Set<String> genres = intersection(viewer.genres(), candidate.taste().genres());
        Set<String> platforms = intersection(viewer.platforms(), candidate.taste().platforms());
        Set<String> types = intersection(viewer.playerTypes(), candidate.taste().playerTypes());
        if (genres.isEmpty() || (kind.requiresSharedPlatform && platforms.isEmpty())) return null;
        long score = kind.genreWeight * jaccard(genres, viewer.genres(), candidate.taste().genres())
                + kind.platformWeight * jaccard(platforms, viewer.platforms(), candidate.taste().platforms())
                + kind.typeWeight * jaccard(types, viewer.playerTypes(), candidate.taste().playerTypes());
        // 0..100 ağırlık * 0..100 jaccard -> 1..10000; ortak tür varken skor hiçbir zaman 0 olmaz.
        int bounded = (int) Math.max(1, Math.min(10000, score));
        return new Item(candidate.userId(), bounded, List.copyOf(genres), List.copyOf(platforms), List.copyOf(types));
    }

    /** Birleşim boşsa 0; aksi halde 0..100 tamsayı yüzde (kayan nokta yok, deterministik). */
    private static long jaccard(Set<String> shared, Set<String> left, Set<String> right) {
        Set<String> union = new TreeSet<>(left);
        union.addAll(right);
        return union.isEmpty() ? 0 : shared.size() * 100L / union.size();
    }

    private static Set<String> intersection(Set<String> left, Set<String> right) {
        Set<String> shared = new TreeSet<>(left);
        shared.retainAll(right);
        return shared;
    }

    private static boolean invalid(String value) {
        return value != null && (value.isBlank() || value.length() > 64 || value.chars().anyMatch(Character::isISOControl));
    }
}
