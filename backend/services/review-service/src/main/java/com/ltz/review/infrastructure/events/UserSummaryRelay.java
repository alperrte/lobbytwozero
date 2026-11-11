package com.ltz.review.infrastructure.events;

import com.ltz.review.infrastructure.persistence.repository.UserSummaryOutbox;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * REV-10: Review -> User {@code ReviewSummaryChanged} teslimi (at-least-once). Bekleyen outbox satırı için görünür inceleme
 * sayısını gönderim anında hesaplar ve User'ın {@code POST /internal/events} ucuna Auth {@code svc+jwt} ile gönderir
 * ({@code aud=user-service}, {@code scope=events:ingest}). Aynı {@code eventId} yeniden denemelerde sabittir; User tekrarı
 * {@code DUPLICATE} sayar ve eski/eşit {@code occurredAt}'ı yok sayar. 401/403/4xx kalıcı hata sayılır (sıkı döngü yok, uzun
 * geri çekilme); 5xx/zaman aşımı geri çekilmeyle yeniden denenir. Kullanıcı işlemleri bu teslimden etkilenmez.
 */
public class UserSummaryRelay {
    static final String AUDIENCE = "user-service";
    static final String EVENT_TYPE = "ReviewSummaryChanged";
    private static final Logger LOG = LoggerFactory.getLogger(UserSummaryRelay.class);
    private static final Duration BASE_BACKOFF = Duration.ofSeconds(5);
    private static final Duration MAX_BACKOFF = Duration.ofMinutes(15);

    private final UserSummaryOutbox outbox;
    private final ServiceTokenClient tokens;
    private final RestClient users;
    private final Clock clock;
    private final int batchSize;

    public UserSummaryRelay(UserSummaryOutbox outbox, ServiceTokenClient tokens, RestClient users, Clock clock, int batchSize) {
        this.outbox = outbox;
        this.tokens = tokens;
        this.users = users;
        this.clock = clock;
        this.batchSize = batchSize;
    }

    /** Zamanlayıcı girişi: hiçbir istisna dışarı sızmaz (bir sonraki turda yeniden denenir). */
    public void run() {
        try {
            publishDue();
        } catch (RuntimeException exception) {
            LOG.warn("Review->User olay turu başarısız: {}", exception.getClass().getSimpleName());
        }
    }

    /** Vadesi gelen olayları gönderir; teslim edilenlerin sayısını döner. */
    public int publishDue() {
        int delivered = 0;
        for (var pending : outbox.due(clock.instant(), batchSize)) {
            String token;
            try {
                token = tokens.tokenFor(AUDIENCE);
            } catch (ServiceTokenClient.TokenUnavailableException _) {
                fail(pending, "TOKEN_UNAVAILABLE");
                break; // Token alınamıyorsa kalan satırlar için Auth'u yoklamayız.
            }
            if (send(pending, token)) delivered++;
        }
        return delivered;
    }

    private boolean send(UserSummaryOutbox.Pending pending, String token) {
        int count = outbox.visibleCount(pending.userId());
        Map<String, Object> envelope = Map.of(
                "eventId", pending.eventId().toString(), "eventType", EVENT_TYPE, "eventVersion", 1,
                "occurredAt", pending.occurredAt().toString(), "producer", "review-service",
                "aggregateId", pending.userId().toString(),
                "payload", Map.of("userId", pending.userId().toString(), "reviewCount", count));
        try {
            users.post().uri("/internal/events").header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON).body(envelope).retrieve().toBodilessEntity();
        } catch (RestClientResponseException exception) {
            int status = exception.getStatusCode().value();
            if (status == 401 || status == 403) tokens.invalidate(AUDIENCE);
            fail(pending, status >= 500 ? "USER_" + status : "USER_REJECTED_" + status);
            return false;
        } catch (RestClientException _) {
            fail(pending, "USER_UNAVAILABLE");
            return false;
        }
        // Gönderim sırasında yeni değişiklik olduysa (event_id değişti) satır bekler ve yeni olayla gider.
        outbox.markDelivered(pending.userId(), pending.eventId(), clock.instant());
        return true;
    }

    private void fail(UserSummaryOutbox.Pending pending, String code) {
        long seconds = Math.min(MAX_BACKOFF.toSeconds(), BASE_BACKOFF.toSeconds() << Math.min(pending.attempts(), 10));
        outbox.markFailed(pending.userId(), pending.eventId(), code.length() > 32 ? code.substring(0, 32) : code, Instant.now(clock).plusSeconds(seconds));
        LOG.warn("Review->User olay teslimi ertelendi: {}", code);
    }
}
