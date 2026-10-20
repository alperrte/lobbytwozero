package com.ltz.game.infrastructure.config;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.http.client.autoconfigure.HttpClientAutoConfiguration;
import org.springframework.boot.http.client.autoconfigure.HttpClientsProperties;
import org.springframework.boot.http.client.autoconfigure.imperative.ImperativeHttpClientAutoConfiguration;
import org.springframework.boot.restclient.autoconfigure.RestClientAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.io.support.ResourcePropertySource;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class RestClientSettingsTest {

    @Test
    void ortamDegiskenleriRestClientZamanAsimlariniBelirler() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        HttpClientAutoConfiguration.class,
                        ImperativeHttpClientAutoConfiguration.class,
                        RestClientAutoConfiguration.class
                ))
                .withInitializer(context -> {
                    try {
                        context.getEnvironment().getPropertySources().addLast(
                                new ResourcePropertySource("classpath:application.properties")
                        );
                    } catch (IOException exception) {
                        throw new UncheckedIOException(exception);
                    }

                    context.getEnvironment().getPropertySources().addFirst(
                            new MapPropertySource("test-env", Map.of(
                                    "REST_CLIENT_CONNECT_TIMEOUT_MS", "1500",
                                    "REST_CLIENT_READ_TIMEOUT_MS", "2400"
                            ))
                    );
                })
                .run(context -> {
                    assertNull(context.getStartupFailure());

                    HttpClientsProperties properties =
                            context.getBean(HttpClientsProperties.class);

                    assertEquals(
                            Duration.ofMillis(1500),
                            properties.getConnectTimeout()
                    );

                    assertEquals(
                            Duration.ofMillis(2400),
                            properties.getReadTimeout()
                    );

                    assertNotNull(context.getBean(RestClient.Builder.class));
                });
    }
}
