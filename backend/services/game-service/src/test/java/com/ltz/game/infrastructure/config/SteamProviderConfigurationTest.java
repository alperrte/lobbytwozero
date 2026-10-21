package com.ltz.game.infrastructure.config;

import com.ltz.game.application.port.GameProvider;
import com.ltz.game.domain.exception.ProviderException;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.*;

class SteamProviderConfigurationTest {
    private final ApplicationContextRunner context = new ApplicationContextRunner()
            .withUserConfiguration(SteamProviderConfiguration.class)
            .withPropertyValues("STEAM_CATALOG_ENABLED=false", "STEAM_WEB_API_KEY=")
            .withBean(RestClient.Builder.class, RestClient::builder);

    @Test
    void disabledByDefaultAndDoesNotMakeRequests() {
        context.run(app -> {
            assertNull(app.getStartupFailure());
            var provider = app.getBean(GameProvider.class);
            var query = new GameProvider.CatalogQuery(0, null, 1);
            assertEquals("PROVIDER_NOT_CONFIGURED", assertThrows(ProviderException.class,
                    () -> provider.catalog(query)).code());
        });
    }

    @Test
    void enabledWithoutKeyFailsClosed() {
        context.withPropertyValues("STEAM_CATALOG_ENABLED=true").run(app ->
                assertNotNull(app.getStartupFailure()));
    }

    @Test
    void enabledWithSyntheticKeyCreatesProviderWithoutCallingSteam() {
        context.withPropertyValues("STEAM_CATALOG_ENABLED=true", "STEAM_WEB_API_KEY=synthetic-test-value")
                .run(app -> {
                    assertNull(app.getStartupFailure());
                    assertEquals("STEAM", app.getBean(GameProvider.class).provider());
                });
    }
}
