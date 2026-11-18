package com.ltz.recommendation.application.service;

import com.ltz.recommendation.application.port.PersonalRatingSource;
import com.ltz.recommendation.application.port.PersonalizationSource;
import com.ltz.recommendation.application.port.PersonalizationSource.Actor;
import com.ltz.recommendation.application.port.PersonalizationSource.Filters;
import com.ltz.recommendation.application.port.RecommendationAlgorithm.Candidate;
import com.ltz.recommendation.application.port.RecommendationAlgorithm.Input;
import com.ltz.recommendation.application.port.RecommendationAlgorithm.Signal;
import com.ltz.recommendation.application.port.RecommendationHistory;
import com.ltz.recommendation.application.port.RecommendationHistory.Feedback;
import com.ltz.recommendation.application.port.RecommendationHistory.FeedbackValue;
import com.ltz.recommendation.application.port.RecommendationHistory.Page;
import com.ltz.recommendation.application.port.RecommendationHistory.Run;
import com.ltz.recommendation.application.port.RecommendationHistory.StoredItem;
import com.ltz.recommendation.domain.exception.RecommendationRequestException;
import com.ltz.recommendation.application.port.LibraryTasteSource;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class PersonalizedRecommendationService {
    private final PersonalizationSource source;
    private final PersonalRatingSource ratings;
    private final LibraryTasteSource tastes;
    private final RecommendationHistory history;
    private final RecommendationExecutionService executions;
    private final RecommendationRateLimiter limits;
    private final TransactionTemplate transactions;

    public PersonalizedRecommendationService(PersonalizationSource source, PersonalRatingSource ratings, LibraryTasteSource tastes,
            RecommendationHistory history, RecommendationExecutionService executions, RecommendationRateLimiter limits,
            TransactionTemplate transactions) {
        this.source = source;
        this.ratings = ratings;
        this.tastes = tastes;
        this.history = history;
        this.executions = executions;
        this.limits = limits;
        this.transactions = transactions;
    }

    public Run generate(Actor actor, Filters filters) {
        limits.acquire(actor.accountId(), true);
        requireConsent(actor);
        var excluded = history.notInterested(actor.accountId());
        var matches = source.candidates(actor, filters).stream().filter(match -> !excluded.contains(match.gameId())).toList();
        // REC-03: kişisel puanlar yalnız aday varsa okunur; Review/Game hatası isteği 503 ile durdurur (puansız sıralama sessizce üretilmez).
        Map<String, Integer> genreRatings = matches.isEmpty() ? Map.of() : ratings.genreAffinity(actor);
        // REC-02/05: favori/tamamlanan tür payları da yalnız aday varsa okunur; Library/Game hatası 503 ile durdurur.
        LibraryTasteSource.Affinity taste = matches.isEmpty() ? LibraryTasteSource.Affinity.NONE : tastes.affinity(actor);
        Map<UUID, PersonalizationSource.Match> byId = matches.stream()
                .collect(Collectors.toMap(PersonalizationSource.Match::gameId, Function.identity()));
        try {
            return transactions.execute(status -> {
            history.lockAccount(actor.accountId());
            source.requireConsent(actor);
            var result = executions.execute(new Input("rules-v1", matches.stream()
                    .map(match -> candidate(match, genreRatings, taste)).toList(), filters.size()));
            List<StoredItem> items = result.items().stream().map(item -> new StoredItem(UUID.randomUUID(), item.targetId(),
                    item.score(), item.rank(), byId.get(item.targetId()).matchedGenres())).toList();
            return history.save(actor.accountId(), new Run(result.runId(),
                    result.algorithmVersion(), result.occurredAt(), filters, items));
            });
        } catch (RecommendationRequestException exception) {
            if ("CONSENT_REQUIRED".equals(exception.code())) purgeHistory(actor.accountId());
            throw exception;
        }
    }

    /** Puan yakınlığı: eşleşen türlerin puan yakınlıklarının ortalaması (puanlanmamış tür 0 sayılır). */
    static Candidate candidate(PersonalizationSource.Match match, Map<String, Integer> genreRatings) {
        return candidate(match, genreRatings, LibraryTasteSource.Affinity.NONE);
    }

    /** REC-02/05: favori ve tamamlanan oyunların tür payları da aynı şekilde eşleşen türler üzerinden ortalanır. */
    static Candidate candidate(PersonalizationSource.Match match, Map<String, Integer> genreRatings,
            LibraryTasteSource.Affinity taste) {
        Map<Signal, Integer> signals = new EnumMap<>(Signal.class);
        signals.put(Signal.GENRE_AFFINITY, match.affinity());
        putPositive(signals, Signal.RATING_AFFINITY, mean(match, genreRatings));
        putPositive(signals, Signal.FAVORITE_AFFINITY, mean(match, taste.favoriteGenres()));
        putPositive(signals, Signal.COMPLETED_AFFINITY, mean(match, taste.completedGenres()));
        return new Candidate(match.gameId(), signals);
    }

    private static int mean(PersonalizationSource.Match match, Map<String, Integer> byGenre) {
        int sum = match.matchedGenres().stream().mapToInt(genre -> byGenre.getOrDefault(genre.toLowerCase(Locale.ROOT), 0)).sum();
        return sum / match.matchedGenres().size();
    }

    private static void putPositive(Map<Signal, Integer> signals, Signal signal, int value) {
        if (value > 0) signals.put(signal, value);
    }

    /** Aynı servis içindeki stateless öneriler de rıza geri çekme temizliğini paylaşır. */
    public void verifyConsent(Actor actor) { requireConsent(actor); }

    public Run read(Actor actor, UUID runId) {
        limits.acquire(actor.accountId(), false);
        requireConsent(actor);
        return history.read(actor.accountId(), runId);
    }

    public Page history(Actor actor, UUID beforeRunId, int size) {
        if (size < 1 || size > 20) throw new IllegalArgumentException("Invalid history page");
        limits.acquire(actor.accountId(), false);
        requireConsent(actor);
        return history.history(actor.accountId(), beforeRunId, size);
    }

    public Feedback feedback(Actor actor, UUID runId, UUID itemId, FeedbackValue value, long version) {
        if (value == null || version < 0) throw new IllegalArgumentException("Invalid feedback");
        limits.acquire(actor.accountId(), false);
        requireConsent(actor);
        return transactions.execute(status -> {
            history.lockAccount(actor.accountId());
            return history.feedback(actor.accountId(), runId, itemId, value, version);
        });
    }

    public Feedback feedbackState(Actor actor, UUID runId, UUID itemId) {
        limits.acquire(actor.accountId(), false);
        requireConsent(actor);
        return history.feedbackState(actor.accountId(), runId, itemId);
    }

    public void erase(UUID owner) {
        purgeHistory(owner);
        limits.erase(owner);
    }

    private void purgeHistory(UUID owner) {
        transactions.executeWithoutResult(status -> {
            history.lockAccount(owner);
            history.erase(owner);
        });
    }

    private void requireConsent(Actor actor) {
        try { source.requireConsent(actor); }
        catch (RecommendationRequestException exception) {
            if ("CONSENT_REQUIRED".equals(exception.code())) purgeHistory(actor.accountId());
            throw exception;
        }
    }
}
