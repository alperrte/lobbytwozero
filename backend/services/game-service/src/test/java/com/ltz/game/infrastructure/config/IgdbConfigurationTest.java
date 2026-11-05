package com.ltz.game.infrastructure.config;

import com.ltz.game.application.port.GameDetailSource;
import com.ltz.game.application.port.GameProvider;
import com.ltz.game.domain.exception.ProviderException;
import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.web.client.RestClient;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** IGDB yalnız iki kimlik bilgisi de varsa kurulur; kimlik yokken uygulama davranışı hiç değişmez. */
class IgdbConfigurationTest {
    private final ApplicationContextRunner context = new ApplicationContextRunner()
            .withUserConfiguration(SteamProviderConfiguration.class, IgdbConfiguration.class)
            .withPropertyValues("STEAM_CATALOG_ENABLED=false", "STEAM_WEB_API_KEY=")
            .withBean(RestClient.Builder.class, RestClient::builder)
            .withBean(Clock.class, Clock::systemUTC);

    @Test void withoutCredentialsNothingIsRegisteredAndDetailsStayUnsupported() {
        context.run(app -> {
            assertNull(app.getStartupFailure());
            assertTrue(app.getBeansOfType(GameDetailSource.class).isEmpty());
            GameProvider provider = app.getBean(GameProvider.class);
            assertFalse(provider.capabilities().contains(GameProvider.Capability.DETAILS));
            assertEquals("STEAM", provider.detailSource());
        });
    }

    @Test void blankOrPartialCredentialsKeepItDisabled() {
        for (String[] values : new String[][]{
                {"ltz.game.igdb.client-id=only-id"},
                {"ltz.game.igdb.client-secret=only-secret"},
                {"ltz.game.igdb.client-id=", "ltz.game.igdb.client-secret="},
                {"ltz.game.igdb.client-id=  ", "ltz.game.igdb.client-secret=synthetic-value"}}) {
            context.withPropertyValues(values).run(app -> {
                assertNull(app.getStartupFailure());
                assertTrue(app.getBeansOfType(GameDetailSource.class).isEmpty());
                assertFalse(app.getBean(GameProvider.class).capabilities().contains(GameProvider.Capability.DETAILS));
            });
        }
    }

    @Test void bothCredentialsAddOnlyTheDetailCapabilityAndKeepSteamAsTheCatalog() {
        context.withPropertyValues("ltz.game.igdb.client-id=synthetic-id", "ltz.game.igdb.client-secret=synthetic-value")
                .run(app -> {
                    assertNull(app.getStartupFailure());
                    assertNotNull(app.getBean(GameDetailSource.class));
                    GameProvider provider = app.getBean(GameProvider.class);
                    assertEquals("STEAM", provider.provider(), "katalog ve kimlik sağlayıcısı değişmez");
                    assertEquals("IGDB", provider.detailSource(), "provenance kaynağı detay kaynağıdır");
                    assertTrue(provider.capabilities().containsAll(java.util.Set.of(
                            GameProvider.Capability.CATALOG, GameProvider.Capability.INCREMENTAL_CATALOG, GameProvider.Capability.DETAILS)));
                    // Katalog çağrısı hâlâ Steam'e gider (burada kapalı olduğu için güvenli reddedilir, IGDB'ye gitmez).
                    var query = new GameProvider.CatalogQuery(0, null, 1);
                    assertEquals("PROVIDER_NOT_CONFIGURED", assertThrows(ProviderException.class, () -> provider.catalog(query)).code());
                });
    }

    @Test void nonHttpsEndpointsAreRejectedAtStartup() {
        context.withPropertyValues("ltz.game.igdb.client-id=synthetic-id", "ltz.game.igdb.client-secret=synthetic-value",
                "ltz.game.igdb.api-url=http://api.example.invalid/v4").run(app -> assertNotNull(app.getStartupFailure()));
        context.withPropertyValues("ltz.game.igdb.client-id=synthetic-id", "ltz.game.igdb.client-secret=synthetic-value",
                "ltz.game.igdb.token-url=http://id.example.invalid/oauth2/token").run(app -> assertNotNull(app.getStartupFailure()));
    }

    @Test void propertiesNeverPrintTheSecret() {
        var properties = new IgdbProperties("synthetic-id", "synthetic-secret-value", "https://api.example.invalid/v4",
                "https://id.example.invalid/token", java.time.Duration.ofSeconds(2), java.time.Duration.ofSeconds(5), java.time.Duration.ZERO);
        assertFalse(properties.toString().contains("synthetic-secret-value"));
    }
}
