package com.ltz.messaging.infrastructure.config;

import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.Executor;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestClient;

import com.ltz.messaging.application.port.BlockCheckPort;
import com.ltz.messaging.application.port.GifProviderPort;
import com.ltz.messaging.application.port.ServiceTokenPort;
import com.ltz.messaging.infrastructure.client.AuthServiceTokenClient;
import com.ltz.messaging.infrastructure.client.GiphyGifClient;
import com.ltz.messaging.infrastructure.client.SocialBlockCheckClient;
import com.ltz.messaging.infrastructure.messaging.realtime.PostgresStreamRelay;
import com.ltz.messaging.infrastructure.messaging.realtime.SseMessageHub;
import com.ltz.messaging.infrastructure.messaging.realtime.StreamRelayListener;

import tools.jackson.databind.ObjectMapper;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
@EnableConfigurationProperties({
        AttachmentProperties.class,
        AuthProperties.class,
        GifProperties.class,
        GroupProperties.class,
        MessageProperties.class,
        ReactionProperties.class,
        RequestLimitProperties.class,
        ServiceClientProperties.class,
        StreamProperties.class,
        VoiceProperties.class
})
public class MessagingApplicationConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    /** Giden çağrılar için Auth'tan hedefe bağlı servis token'ı alır (client credentials). */
    @Bean
    ServiceTokenPort authServiceTokenClient(RestClient.Builder builder, Clock clock,
                                            ServiceClientProperties properties) {
        return new AuthServiceTokenClient(builder, clock, properties);
    }

    /**
     * Engel durumu social-service'ten sorulur (MSG-15). Adaptör {@code ltz.messaging.blocks.adapter} ile seçilir;
     * üretimde tek değer {@code social}'dır, testler kendi sahte adaptörünü bağlar.
     */
    @Bean
    @ConditionalOnProperty(name = "ltz.messaging.blocks.adapter", havingValue = "social", matchIfMissing = true)
    BlockCheckPort socialBlockCheckClient(RestClient.Builder builder, ServiceTokenPort serviceTokens,
                                          ServiceClientProperties properties) {
        return new SocialBlockCheckClient(builder, serviceTokens, properties);
    }

    /**
     * GIF platformu (MSG-22). Adaptör {@code ltz.messaging.gifs.adapter} ile seçilir; üretimde tek değer {@code giphy}'dir,
     * testler kendi sahte adaptörünü bağlar. Anahtar yoksa adaptör kurulur ama her çağrı fail-closed reddedilir.
     */
    @Bean
    @ConditionalOnProperty(name = "ltz.messaging.gifs.adapter", havingValue = "giphy", matchIfMissing = true)
    GifProviderPort giphyGifClient(RestClient.Builder builder, GifProperties properties) {
        return new GiphyGifClient(builder, properties);
    }

    /**
     * Akışlara iletim yürütücüsü: sınırlı iş parçacığı ve kuyruk. Kuyruk dolarsa iletim reddedilir ve etkilenen akışlar
     * kapanır (istemci yeniden bağlanıp eşitlenir); sınırsız bellek büyümesi olmaz.
     */
    @Bean
    ThreadPoolTaskExecutor streamDispatchExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("message-stream-");
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(1000);
        return executor;
    }

    /** Gerçek zamanlı akış (SSE) kaydı; docs/decisions/0001-gercek-zamanli-mesajlasma.md. */
    @Bean
    SseMessageHub sseMessageHub(StreamProperties properties, Executor streamDispatchExecutor, Clock clock) {
        return new SseMessageHub(properties, streamDispatchExecutor, clock);
    }

    /** Aktarım yazımı tek iş parçacığında: olaylar üretildikleri sırayla yazılır (ör. mesaj, sonra tepkisi). */
    @Bean
    @ConditionalOnProperty(name = "ltz.messaging.stream.relay", havingValue = "postgres", matchIfMissing = true)
    ThreadPoolTaskExecutor streamRelayExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("message-relay-");
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(1000);
        return executor;
    }

    /**
     * Birden çok örnekte canlı akış (ADR 0001): olaylar PostgreSQL üzerinden tüm örneklere aktarılır. Tek örnekte
     * {@code ltz.messaging.stream.relay=local} ile {@link SseMessageHub} doğrudan kullanılır.
     */
    @Bean
    @Primary
    @ConditionalOnProperty(name = "ltz.messaging.stream.relay", havingValue = "postgres", matchIfMissing = true)
    PostgresStreamRelay postgresStreamRelay(SseMessageHub hub, JdbcTemplate jdbc, TransactionTemplate transactions,
                                            ObjectMapper mapper, Executor streamRelayExecutor) {
        return new PostgresStreamRelay(hub, jdbc, transactions, mapper, streamRelayExecutor);
    }

    /** Havuz dışı tek bağlantıyla dinler (havuz küçülmez); bağlam kapanırken durur. */
    @Bean
    @ConditionalOnProperty(name = "ltz.messaging.stream.relay", havingValue = "postgres", matchIfMissing = true)
    StreamRelayListener streamRelayListener(@Value("${spring.datasource.url}") String url,
                                            @Value("${spring.datasource.username}") String username,
                                            @Value("${spring.datasource.password:}") String password,
                                            SseMessageHub hub) {
        return new StreamRelayListener(url, username, password, hub, Duration.ofSeconds(1));
    }
}
