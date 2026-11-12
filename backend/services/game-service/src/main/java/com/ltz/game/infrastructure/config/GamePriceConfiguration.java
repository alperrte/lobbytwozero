package com.ltz.game.infrastructure.config;

import com.ltz.game.application.port.GamePriceProvider;
import com.ltz.game.infrastructure.client.ItadPriceClient;
import com.ltz.game.infrastructure.persistence.repository.GameExternalReferenceRepository;
import java.time.Clock;
import java.time.Duration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration(proxyBeanMethods=false) @EnableConfigurationProperties({GamePriceProperties.class,ItadRateProperties.class,PriceDemandProperties.class})
public class GamePriceConfiguration {
    @Bean GamePriceProvider itadPrices(RestClient.Builder builder,GamePriceProperties properties,GameExternalReferenceRepository refs,Clock clock,ItadRateProperties rate) {
        return new ItadPriceClient(GameRatingConfiguration.restClient(builder,properties.apiUrl(),Duration.ofSeconds(1),Duration.ofSeconds(3)),properties,refs,clock,rate);
    }
}
