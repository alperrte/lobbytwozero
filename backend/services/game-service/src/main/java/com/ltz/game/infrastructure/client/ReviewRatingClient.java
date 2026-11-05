package com.ltz.game.infrastructure.client;

import com.ltz.game.application.port.ReviewRatingPort;
import com.ltz.game.application.port.ServiceTokenPort;
import com.ltz.game.domain.exception.ReviewRatingUnavailableException;
import com.ltz.game.domain.exception.ServiceTokenUnavailableException;
import com.ltz.game.infrastructure.client.dto.response.ReviewRatingPageResponse;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * Review'ın imleçli puan dışa aktarma ucunu servis token'ıyla okur. Bounded zaman aşımı RestClient'ta yapılandırılır.
 * 401/403'te yeniden deneme ve kullanıcı token'ına geri düşme yoktur; yalnız 5xx/zaman aşımında en çok bir kısa
 * yeniden deneme yapılır. Yanıt güvenilmez girdidir: sıra, aralık ve imleç ilerlemesi doğrulanır. Hata iletilerine
 * token, gizli anahtar veya yanıt gövdesi yazılmaz.
 */
public class ReviewRatingClient implements ReviewRatingPort {

    static final String PATH = "/internal/v1/games/rating-summaries";
    private static final BigDecimal MIN_AVERAGE = BigDecimal.ONE;
    private static final BigDecimal MAX_AVERAGE = BigDecimal.TEN;
    private static final int MAX_SCALE = 2;
    private static final int MAX_PAGE = 500;

    private final RestClient restClient;
    private final ServiceTokenPort tokens;
    private final String audience;
    private final Duration retryDelay;

    public ReviewRatingClient(RestClient restClient, ServiceTokenPort tokens, String audience, Duration retryDelay) {
        this.restClient = restClient;
        this.tokens = tokens;
        this.audience = audience;
        this.retryDelay = retryDelay;
    }

    @Override
    public RatingPage fetchPage(UUID after, int limit) {
        if (limit < 1 || limit > MAX_PAGE) {
            throw new IllegalArgumentException("Sayfa büyüklüğü 1–500 olmalıdır.");
        }
        ReviewRatingPageResponse response;
        try {
            response = request(after, limit, true);
        } catch (ServiceTokenUnavailableException exception) {
            throw new ReviewRatingUnavailableException(ReviewRatingUnavailableException.TOKEN_UNAVAILABLE, exception);
        }
        return validated(response, after, limit);
    }

    private ReviewRatingPageResponse request(UUID after, int limit, boolean mayRetry) {
        String token = tokens.tokenFor(audience);
        try {
            return restClient.get()
                    .uri(builder -> {
                        builder.path(PATH).queryParam("limit", limit);
                        if (after != null) {
                            builder.queryParam("after", after);
                        }
                        return builder.build();
                    })
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .retrieve()
                    .body(ReviewRatingPageResponse.class);
        } catch (RestClientResponseException exception) {
            int status = exception.getStatusCode().value();
            if (status == 401 || status == 403) {
                // Token reddedildi: bellekteki token atılır, aynı çağrıda yeniden deneme ve fallback yoktur.
                tokens.invalidate(audience);
                throw new ReviewRatingUnavailableException(ReviewRatingUnavailableException.REVIEW_REJECTED, exception);
            }
            if (status >= 500 && mayRetry) {
                return retryOnce(after, limit);
            }
            throw new ReviewRatingUnavailableException(status >= 500
                    ? ReviewRatingUnavailableException.REVIEW_UNAVAILABLE
                    : ReviewRatingUnavailableException.INVALID_RESPONSE, exception);
        } catch (ResourceAccessException exception) {
            if (mayRetry) {
                return retryOnce(after, limit);
            }
            throw new ReviewRatingUnavailableException(ReviewRatingUnavailableException.REVIEW_UNAVAILABLE, exception);
        } catch (RestClientException exception) {
            throw new ReviewRatingUnavailableException(ReviewRatingUnavailableException.INVALID_RESPONSE, exception);
        }
    }

    private ReviewRatingPageResponse retryOnce(UUID after, int limit) {
        try {
            Thread.sleep(retryDelay);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new ReviewRatingUnavailableException(ReviewRatingUnavailableException.REVIEW_UNAVAILABLE, interrupted);
        }
        return request(after, limit, false);
    }

    private static RatingPage validated(ReviewRatingPageResponse response, UUID after, int limit) {
        if (response == null || response.items() == null || response.items().size() > limit) {
            throw invalid();
        }
        List<RatingSummary> items = new ArrayList<>(response.items().size());
        String previous = after == null ? null : after.toString();
        for (ReviewRatingPageResponse.Item item : response.items()) {
            RatingSummary summary = summary(item, previous);
            previous = summary.gameId().toString();
            items.add(summary);
        }
        UUID next = response.nextAfter();
        // Son sayfa dışında imleç, tam sayfanın son oyunudur; aksi halde tarama ilerlemeyebilir.
        if (next != null && (items.size() != limit || !next.equals(items.getLast().gameId()))) {
            throw invalid();
        }
        return new RatingPage(items, next);
    }

    private static RatingSummary summary(ReviewRatingPageResponse.Item item, String previous) {
        if (item == null || item.gameId() == null || item.averageRating() == null || item.ratingCount() == null) {
            throw invalid();
        }
        // UUID.compareTo işaretli karşılaştırır; PostgreSQL uuid sırası kanonik küçük harf metin sırasıdır.
        boolean ascending = previous == null || item.gameId().toString().compareTo(previous) > 0;
        if (!ascending || item.ratingCount() < 1 || !validAverage(item.averageRating())) {
            throw invalid();
        }
        return new RatingSummary(item.gameId(), item.averageRating(), item.ratingCount());
    }

    private static boolean validAverage(BigDecimal average) {
        return average.compareTo(MIN_AVERAGE) >= 0 && average.compareTo(MAX_AVERAGE) <= 0
                && average.stripTrailingZeros().scale() <= MAX_SCALE;
    }

    private static ReviewRatingUnavailableException invalid() {
        return new ReviewRatingUnavailableException(ReviewRatingUnavailableException.INVALID_RESPONSE);
    }
}
