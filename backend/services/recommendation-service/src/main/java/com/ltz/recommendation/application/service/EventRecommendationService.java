package com.ltz.recommendation.application.service;

import com.ltz.recommendation.application.port.EventDiscoverySource;
import com.ltz.recommendation.application.port.EventDiscoverySource.Event;
import com.ltz.recommendation.application.port.GameMetadataSource;
import com.ltz.recommendation.application.port.PersonalizationSource.Actor;
import com.ltz.recommendation.application.port.PlayerCandidateSource;
import com.ltz.recommendation.application.port.PlayerCandidateSource.Taste;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * REC-15: izleyicinin tür ve platform tercihine uyan yaklaşan etkinlikler. Saklanmaz (stateless). Yalnız Event'in
 * gerçekten sunduğu veriye dayanır: PLANNED/ACTIVE ve bitmemiş, kendi düzenlemediği ve Event'in izleyici için
 * {@code joinable} saydığı (zaten katılmamış, dolu olmayan) etkinlikler. Katılım cezası öneriyle ilgisizdir: cezalı
 * kullanıcıya da öneri yapılır (katılım denetimi Event'te). Öneri katılım mutasyonu yapmaz.
 */
@Service
public class EventRecommendationService {
    public static final String ALGORITHM_VERSION = "event-affinity-v1";
    public static final int DEFAULT_SIZE = 10;
    public static final int MAX_SIZE = 20;
    static final int GENRE_WEIGHT = 60;
    static final int PLATFORM_WEIGHT = 40;
    private static final int GAME_BATCH = 100;

    public record Item(UUID eventId, String type, String title, Instant startsAt, Instant endsAt, UUID gameId, int score,
            List<String> sharedGenres, List<String> sharedPlatforms) {}

    public record Result(String algorithmVersion, Instant createdAt, List<Item> items, List<String> limitations) {}

    private final EventDiscoverySource events;
    private final PlayerCandidateSource viewers;
    private final GameMetadataSource games;
    private final PersonalizedRecommendationService consent;
    private final RecommendationRateLimiter limits;
    private final Clock clock;

    @Autowired
    public EventRecommendationService(EventDiscoverySource events, PlayerCandidateSource viewers, GameMetadataSource games,
            PersonalizedRecommendationService consent, RecommendationRateLimiter limits) {
        this(events, viewers, games, consent, limits, Clock.systemUTC());
    }

    EventRecommendationService(EventDiscoverySource events, PlayerCandidateSource viewers, GameMetadataSource games,
            PersonalizedRecommendationService consent, RecommendationRateLimiter limits, Clock clock) {
        this.events = events;
        this.viewers = viewers;
        this.games = games;
        this.consent = consent;
        this.limits = limits;
        this.clock = clock;
    }

    public Result recommend(Actor actor, Integer size) {
        int limit = size == null ? DEFAULT_SIZE : size;
        if (limit < 1 || limit > MAX_SIZE) throw new IllegalArgumentException("Invalid size");
        limits.acquire(actor.accountId(), true);
        consent.verifyConsent(actor);
        Taste viewer = viewers.viewer(actor);
        if (viewer.genres().isEmpty() && viewer.platforms().isEmpty()) {
            return result(List.of(), List.of("VIEWER_PREFERENCES_MISSING"));
        }
        var scan = events.upcoming(actor);
        Instant now = clock.instant();
        List<Event> open = scan.events().stream().filter(event -> open(event, actor, now)).toList();
        Map<UUID, List<String>> genres = genres(open);
        List<Item> items = new ArrayList<>();
        for (Event event : open) {
            // Game katalogunda görünmeyen veya yetişkin oyuna bağlı etkinlik önerilmez.
            if (event.gameId() != null && !genres.containsKey(event.gameId())) continue;
            Item item = score(viewer, event, event.gameId() == null ? List.of() : genres.get(event.gameId()));
            if (item != null) items.add(item);
        }
        items.sort(Comparator.comparingInt(Item::score).reversed().thenComparing(Item::startsAt)
                .thenComparing(item -> item.eventId().toString()));
        List<String> limitations = new ArrayList<>();
        if (scan.truncated()) limitations.add("EVENT_SCAN_TRUNCATED");
        return result(items.stream().limit(limit).toList(), limitations);
    }

    private static boolean open(Event event, Actor actor, Instant now) {
        return (event.status().equals("PLANNED") || event.status().equals("ACTIVE")) && event.endsAt().isAfter(now)
                && !actor.accountId().equals(event.organizerId()) && event.joinable();
    }

    private Map<UUID, List<String>> genres(List<Event> open) {
        List<UUID> ids = open.stream().map(Event::gameId).filter(java.util.Objects::nonNull).distinct().toList();
        Map<UUID, List<String>> found = new HashMap<>();
        for (int from = 0; from < ids.size(); from += GAME_BATCH) {
            found.putAll(games.genres(List.copyOf(ids.subList(from, Math.min(ids.size(), from + GAME_BATCH)))));
        }
        return found;
    }

    static Item score(Taste viewer, Event event, List<String> gameGenres) {
        Set<String> viewerGenres = normalized(viewer.genres());
        Set<String> eventGenres = normalized(gameGenres);
        Set<String> viewerPlatforms = normalized(viewer.platforms());
        Set<String> eventPlatforms = normalized(event.platforms());
        Set<String> sharedGenres = intersection(viewerGenres, eventGenres);
        Set<String> sharedPlatforms = intersection(viewerPlatforms, eventPlatforms);
        if (sharedGenres.isEmpty() && sharedPlatforms.isEmpty()) return null;
        long score = GENRE_WEIGHT * jaccard(sharedGenres, viewerGenres, eventGenres)
                + PLATFORM_WEIGHT * jaccard(sharedPlatforms, viewerPlatforms, eventPlatforms);
        return new Item(event.id(), event.type(), event.title(), event.startsAt(), event.endsAt(), event.gameId(),
                (int) Math.max(1, Math.min(10000, score)), List.copyOf(sharedGenres), List.copyOf(sharedPlatforms));
    }

    /** Küçük harf ve harf/rakam dışı karakterler atılır: NINTENDO_SWITCH ile nintendo-switch eşleşir. */
    static Set<String> normalized(Collection<String> values) {
        Set<String> result = new TreeSet<>();
        for (String value : values) {
            String key = value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
            if (!key.isEmpty()) result.add(key);
        }
        return result;
    }

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

    private Result result(List<Item> items, List<String> limitations) {
        return new Result(ALGORITHM_VERSION, clock.instant(), items, limitations);
    }
}
