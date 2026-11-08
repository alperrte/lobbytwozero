package com.ltz.game.infrastructure.config;

import com.ltz.game.application.port.ReviewRatingPort;
import com.ltz.game.application.port.ServiceTokenPort;
import com.ltz.game.infrastructure.client.AuthServiceTokenClient;
import com.ltz.game.infrastructure.client.ReviewRatingClient;
import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Game → Review puan okuması için Auth token istemcisi ve Review adaptörü. İkisi de bounded bağlantı/okuma zaman
 * aşımıyla ve yönlendirmeleri izlemeyen (SECURITY.md §8) ayrı RestClient'larla kurulur.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(GameRatingProperties.class)
public class GameRatingConfiguration {

    private static final Duration RETRY_DELAY = Duration.ofMillis(300);

    @Bean
    ServiceTokenPort authServiceTokenClient(RestClient.Builder builder, Clock clock, GameRatingProperties properties) {
        return new AuthServiceTokenClient(restClient(builder, properties.authServiceUrl(), properties.connectTimeout(), properties.readTimeout()), clock, properties);
    }

    @Bean
    ReviewRatingPort reviewRatingClient(RestClient.Builder builder, ServiceTokenPort tokens, GameRatingProperties properties,
            @org.springframework.beans.factory.annotation.Value("${ltz.game.rating.summary-path:/internal/v1/games/rating-summaries}") String summaryPath) {
        return new ReviewRatingClient(restClient(builder, properties.reviewServiceUrl(), properties.connectTimeout(), properties.readTimeout()), tokens,
                properties.audience(), RETRY_DELAY, summaryPath);
    }

    /** Bounded bağlantı/okuma zaman aşımı olan, yönlendirmeleri izlemeyen RestClient. */
    public static RestClient restClient(RestClient.Builder builder, String baseUrl, Duration connectTimeout, Duration readTimeout) {
        // HttpClient fabrikayla aynı ömürdedir (uygulama boyunca); bu yüzden burada kapatılmaz.
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(connectTimeout)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build());
        factory.setReadTimeout(readTimeout);
        return builder.clone().baseUrl(baseUrl).requestFactory(factory).build();
    }
}
