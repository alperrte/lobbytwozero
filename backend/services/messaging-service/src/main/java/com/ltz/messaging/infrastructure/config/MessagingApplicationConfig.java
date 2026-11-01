package com.ltz.messaging.infrastructure.config;

import java.time.Clock;
import java.util.concurrent.Executor;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.client.RestClient;

import com.ltz.messaging.application.port.BlockCheckPort;
import com.ltz.messaging.application.port.MessageStreamPort;
import com.ltz.messaging.application.port.ServiceTokenPort;
import com.ltz.messaging.infrastructure.client.AuthServiceTokenClient;
import com.ltz.messaging.infrastructure.client.SocialBlockCheckClient;
import com.ltz.messaging.infrastructure.messaging.realtime.SseMessageHub;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
@EnableConfigurationProperties({
        AuthProperties.class,
        GroupProperties.class,
        MessageProperties.class,
        RequestLimitProperties.class,
        ServiceClientProperties.class,
        StreamProperties.class
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
    MessageStreamPort sseMessageHub(StreamProperties properties, Executor streamDispatchExecutor, Clock clock) {
        return new SseMessageHub(properties, streamDispatchExecutor, clock);
    }
}
