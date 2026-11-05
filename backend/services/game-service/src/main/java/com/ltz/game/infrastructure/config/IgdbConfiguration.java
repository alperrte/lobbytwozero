package com.ltz.game.infrastructure.config;

import com.ltz.game.application.port.GameDetailSource;
import com.ltz.game.application.port.GameProvider;
import com.ltz.game.infrastructure.client.DetailEnrichedGameProvider;
import com.ltz.game.infrastructure.client.IgdbDetailSource;
import java.time.Clock;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.web.client.RestClient;

/**
 * IGDB detay kaynağı yalnız Twitch kimlik bilgilerinin ikisi de tanımlıysa kurulur; aksi halde hiçbir bean, istek
 * veya davranış değişikliği olmaz (fail-closed). Katalog sağlayıcısı (Steam) aynen kalır, yalnız detay yeteneği eklenir.
 */
@Configuration(proxyBeanMethods = false)
@Conditional(IgdbConfiguration.CredentialsPresent.class)
@EnableConfigurationProperties(IgdbProperties.class)
public class IgdbConfiguration {

    /** İki kimlik bilgisi de dolu mu; değerler koşul ifadesine gömülmez (enjeksiyon yok). */
    static final class CredentialsPresent implements Condition {
        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            return filled(context, "ltz.game.igdb.client-id") && filled(context, "ltz.game.igdb.client-secret");
        }

        private static boolean filled(ConditionContext context, String key) {
            String value = context.getEnvironment().getProperty(key);
            return value != null && !value.isBlank();
        }
    }

    @Bean
    GameDetailSource igdbDetailSource(RestClient.Builder builder, Clock clock, IgdbProperties properties) {
        RestClient http = GameRatingConfiguration.restClient(builder, properties.apiUrl(),
                properties.connectTimeout(), properties.readTimeout());
        return new IgdbDetailSource(http, clock, properties.clientId(), properties.clientSecret(),
                properties.apiUrl(), properties.tokenUrl(), properties.minInterval());
    }

    @Bean
    @Primary
    GameProvider detailEnrichedGameProvider(@Qualifier("steamGameProvider") GameProvider steam, GameDetailSource detail) {
        return new DetailEnrichedGameProvider(steam, detail);
    }
}
