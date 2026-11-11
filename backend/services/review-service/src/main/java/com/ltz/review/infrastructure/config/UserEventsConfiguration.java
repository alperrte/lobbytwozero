package com.ltz.review.infrastructure.config;

import com.ltz.review.infrastructure.events.ServiceTokenClient;
import com.ltz.review.infrastructure.events.UserSummaryRelay;
import com.ltz.review.infrastructure.persistence.repository.UserSummaryOutbox;
import java.time.Clock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.client.RestClient;

/**
 * REV-10 Review -> User olay teslimi. Auth istemci secret'ı ({@code AUTH_REVIEW_SERVICE_CLIENT_SECRET}) boşsa hiçbir bean
 * kurulmaz: Auth'a da User'a da istek atılmaz; outbox satırları yalnız birikir ve secret verilince gönderilir. Secret'ın
 * ortama eklenmesi ENV değişikliğidir ve kullanıcı onayı gerektirir; bu sınıf hiçbir ENV değerini değiştirmez.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnExpression("!'${ltz.review.user-events.client-secret:}'.isBlank()")
public class UserEventsConfiguration {
    @Bean
    ServiceTokenClient reviewServiceTokenClient(RestClient.Builder builder, Clock clock,
            @Value("${ltz.review.user-events.auth-url}") String authUrl,
            @Value("${ltz.review.user-events.client-secret}") String secret) {
        return new ServiceTokenClient(builder.baseUrl(authUrl).build(), clock, "review-service", secret);
    }

    @Bean
    UserSummaryRelay userSummaryRelay(UserSummaryOutbox outbox, ServiceTokenClient tokens, RestClient.Builder builder, Clock clock,
            @Value("${ltz.review.user-events.user-url}") String userUrl,
            @Value("${ltz.review.user-events.batch-size:50}") int batchSize) {
        return new UserSummaryRelay(outbox, tokens, builder.baseUrl(userUrl).build(), clock, batchSize);
    }

    @Bean
    UserSummaryRelayScheduler userSummaryRelayScheduler(UserSummaryRelay relay) { return new UserSummaryRelayScheduler(relay); }

    /** Zamanlayıcı kabuğu: tur süresi yapılandırmadan gelir. */
    static class UserSummaryRelayScheduler {
        private final UserSummaryRelay relay;

        UserSummaryRelayScheduler(UserSummaryRelay relay) { this.relay = relay; }

        @Scheduled(fixedDelayString = "${ltz.review.user-events.interval:5s}")
        void tick() { relay.run(); }
    }
}
