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
import java.util.List;
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
    private final RecommendationHistory history;
    private final RecommendationExecutionService executions;
    private final RecommendationRateLimiter limits;
    private final TransactionTemplate transactions;

    public PersonalizedRecommendationService(PersonalizationSource source, PersonalRatingSource ratings, RecommendationHistory history,
            RecommendationExecutionService executions, RecommendationRateLimiter limits, TransactionTemplate transactions) {
        this.source = source;
        this.ratings = ratings;
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
        Map<UUID, PersonalizationSource.Match> byId = matches.stream()
                .collect(Collectors.toMap(PersonalizationSource.Match::gameId, Function.identity()));
        try {
            return transactions.execute(status -> {
            history.lockAccount(actor.accountId());
            source.requireConsent(actor);
            var result = executions.execute(new Input("rules-v1", matches.stream()
                    .map(match -> candidate(match, genreRatings)).toList(), filters.size()));
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
        int sum = match.matchedGenres().stream().mapToInt(genre -> genreRatings.getOrDefault(genre.toLowerCase(java.util.Locale.ROOT), 0)).sum();
        int rating = sum / match.matchedGenres().size();
        return rating > 0
                ? new Candidate(match.gameId(), Map.of(Signal.GENRE_AFFINITY, match.affinity(), Signal.RATING_AFFINITY, rating))
                : new Candidate(match.gameId(), Map.of(Signal.GENRE_AFFINITY, match.affinity()));
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
