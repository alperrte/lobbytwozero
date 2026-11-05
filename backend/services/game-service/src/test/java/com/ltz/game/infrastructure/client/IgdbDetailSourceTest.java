package com.ltz.game.infrastructure.client;

import com.ltz.game.application.port.GameProvider;
import com.ltz.game.domain.enums.LanguageSupportStatus;
import com.ltz.game.domain.exception.ProviderException;
import com.ltz.game.infrastructure.client.FakeServer.Reply;
import com.ltz.game.infrastructure.config.GameRatingConfiguration;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** IGDB detay kaynağı: sahte Twitch ve IGDB sunucuları; gerçek ağ, gerçek anahtar veya canlı IGDB çağrısı yoktur. */
class IgdbDetailSourceTest {
    private static final String SECRET = "test-only-secret-value";
    private static final String TOKEN = "{\"access_token\":\"tok-1\",\"expires_in\":5000000,\"token_type\":\"bearer\"}";
    private static final String STEAM_SOURCE = "[{\"id\":1,\"name\":\"Steam\"}]";
    private static final String EXTERNAL = "[{\"id\":9,\"game\":777,\"uid\":\"570\"}]";
    private static final String GAME = "[{\"id\":777,\"summary\":\"  A summary.  \",\"first_release_date\":1373241600,"
            + "\"involved_companies\":["
            + "{\"id\":1,\"developer\":true,\"publisher\":false,\"company\":{\"id\":5,\"name\":\"Valve\"}},"
            + "{\"id\":2,\"developer\":false,\"publisher\":true,\"company\":{\"id\":6,\"name\":\"Valve Publishing\"}},"
            + "{\"id\":3,\"developer\":false,\"publisher\":false,\"company\":{\"id\":7,\"name\":\"Porting House\"}}],"
            + "\"language_supports\":["
            + "{\"language\":{\"locale\":\"en-US\"},\"language_support_type\":{\"name\":\"Audio\"}},"
            + "{\"language\":{\"locale\":\"en-US\"},\"language_support_type\":{\"name\":\"Interface\"}},"
            + "{\"language\":{\"locale\":\"tr_TR\"},\"language_support_type\":{\"name\":\"Subtitles\"}},"
            + "{\"language\":{\"locale\":\"de-DE\"},\"language_support_type\":{\"name\":\"Mystery\"}},"
            + "{\"language\":{\"locale\":\"not a locale!\"},\"language_support_type\":{\"name\":\"Audio\"}}]}]";

    private static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-11-05T10:00:00Z");

        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
        void advance(Duration duration) { now = now.plus(duration); }
    }

    /** İki sahte sunucu + kaynak; kapatma try-with-resources ile yapılır. */
    private static final class Rig implements AutoCloseable {
        final FakeServer api = new FakeServer();
        final FakeServer token = new FakeServer();
        final MutableClock clock = new MutableClock();
        final IgdbDetailSource source;

        Rig(Duration interval) throws IOException {
            this(interval, false);
        }

        /** {@code happy}: kaynak kimliği, eşleme ve oyun yanıtları sıraya konur. */
        Rig(Duration interval, boolean happy) throws IOException {
            RestClient http = GameRatingConfiguration.restClient(RestClient.builder(), api.baseUrl(), Duration.ofSeconds(1), Duration.ofSeconds(2));
            source = new IgdbDetailSource(http, clock, "test-client", SECRET, api.baseUrl() + "/v4", token.baseUrl() + "/oauth2/token", interval);
            token.enqueue(Reply.json(200, TOKEN));
            if (happy) {
                api.enqueue(Reply.json(200, STEAM_SOURCE), Reply.json(200, EXTERNAL), Reply.json(200, GAME));
            }
        }

        String code(String appId) {
            return assertThrows(ProviderException.class, () -> source.details(appId)).code();
        }

        @Override public void close() {
            api.close();
            token.close();
        }
    }

    @Test void mapsSteamAppIdToDetailsCompaniesAndLanguagesWithoutInventingAnything() throws IOException {
        try (Rig rig = new Rig(Duration.ZERO, true)) {
            GameProvider.Details details = rig.source.details("570");
            assertEquals("A summary.", details.description());
            assertEquals(LocalDate.of(2013, 7, 8), details.releaseDate());
            assertEquals(Set.of("Valve"), details.developers());
            assertEquals(Set.of("Valve Publishing"), details.publishers());
            assertNull(details.providerType());
            // en-us: arayüz+ses var, altyazı listelenmedi -> UNKNOWN; tr-tr yalnız altyazı; bilinmeyen tür ve bozuk dil atlandı.
            assertEquals(List.of(
                    new GameProvider.LanguageSupport("en-us", LanguageSupportStatus.SUPPORTED, LanguageSupportStatus.SUPPORTED, LanguageSupportStatus.UNKNOWN),
                    new GameProvider.LanguageSupport("tr-tr", LanguageSupportStatus.UNKNOWN, LanguageSupportStatus.UNKNOWN, LanguageSupportStatus.SUPPORTED)),
                    details.languages());
        }
    }

    @Test void sendsDocumentedRequestsWithHeadersAndKeepsTheSecretOutOfTheUrl() throws IOException {
        try (Rig rig = new Rig(Duration.ZERO, true)) {
            rig.source.details("570");
            var requests = rig.api.received();
            assertEquals(List.of("/v4/external_game_sources", "/v4/external_games", "/v4/games"),
                    requests.stream().map(FakeServer.Received::pathAndQuery).toList());
            requests.forEach(request -> {
                assertEquals("POST", request.method());
                assertEquals("Bearer tok-1", request.authorization());
            });
            assertEquals("fields name; where name = \"Steam\"; limit 5;", requests.get(0).body());
            assertEquals("fields game,uid; where external_game_source = 1 & uid = \"570\"; limit 10;", requests.get(1).body());
            assertTrue(requests.get(2).body().contains("where id = 777; limit 1;"));
            assertTrue(requests.get(2).body().contains("language_supports.language.locale"));
            var tokenRequest = rig.token.received().getFirst();
            assertEquals("POST", tokenRequest.method());
            assertEquals("/oauth2/token", tokenRequest.pathAndQuery(), "gizli anahtar URL'de değil");
            assertTrue(tokenRequest.body().contains("grant_type=client_credentials"));
            assertTrue(tokenRequest.body().contains("client_id=test-client"));
            assertTrue(tokenRequest.body().contains("client_secret=" + SECRET));
        }
    }

    @Test void reusesTokenAndSteamSourceIdAcrossGamesThenRefreshesTheTokenBeforeExpiry() throws IOException {
        try (Rig rig = new Rig(Duration.ZERO, true)) {
            rig.source.details("570");
            rig.api.enqueue(Reply.json(200, "[{\"id\":10,\"game\":777,\"uid\":\"730\"}]"), Reply.json(200, GAME));
            rig.source.details("730");
            assertEquals(5, rig.api.received().size(), "kaynak kimliği ikinci oyunda yeniden sorgulanmadı");
            assertEquals(1, rig.token.received().size(), "token yeniden kullanıldı");
            rig.token.enqueue(Reply.json(200, TOKEN.replace("tok-1", "tok-2")));
            rig.clock.advance(Duration.ofSeconds(5_000_000)); // süre doldu
            rig.api.enqueue(Reply.json(200, EXTERNAL), Reply.json(200, GAME));
            rig.source.details("570");
            assertEquals(2, rig.token.received().size());
            assertEquals("Bearer tok-2", rig.api.received().getLast().authorization());
        }
    }

    @Test void zeroUnmatchedOrAmbiguousCandidatesAreNeverAutoMapped() throws IOException {
        try (Rig rig = new Rig(Duration.ZERO)) {
            rig.api.enqueue(Reply.json(200, STEAM_SOURCE), Reply.json(200, "[]"));
            assertEquals("PROVIDER_ITEM_NOT_FOUND", rig.code("570"));
            rig.api.enqueue(Reply.json(200, "[{\"game\":1,\"uid\":\"999\"}]")); // başka uid: kanıt değil
            assertEquals("PROVIDER_ITEM_NOT_FOUND", rig.code("570"));
            rig.api.enqueue(Reply.json(200, "[{\"game\":1,\"uid\":\"570\"},{\"game\":2,\"uid\":\"570\"}]"));
            assertEquals("AMBIGUOUS_PROVIDER_MAPPING", rig.code("570"));
            rig.api.enqueue(Reply.json(200, "[{\"game\":1,\"uid\":\"570\"},{\"game\":1,\"uid\":\"570\"}]"), Reply.json(200, GAME));
            assertEquals("A summary.", rig.source.details("570").description(), "aynı oyunun tekrarı belirsizlik değildir");
        }
    }

    @Test void unknownOrAmbiguousSteamSourceFailsClosed() throws IOException {
        try (Rig rig = new Rig(Duration.ZERO)) {
            rig.api.enqueue(Reply.json(200, "[]"));
            assertEquals("INVALID_PROVIDER_RESPONSE", rig.code("570"));
            rig.api.enqueue(Reply.json(200, "[{\"id\":1,\"name\":\"Steam\"},{\"id\":2,\"name\":\"steam\"}]"));
            assertEquals("AMBIGUOUS_PROVIDER_MAPPING", rig.code("570"));
            rig.api.enqueue(Reply.json(200, "[{\"id\":3,\"name\":\"Steam Deck\"}]"));
            assertEquals("INVALID_PROVIDER_RESPONSE", rig.code("570"));
        }
    }

    @Test void invalidAppIdsNeverReachTheNetworkOrTheQueryLanguage() throws IOException {
        try (Rig rig = new Rig(Duration.ZERO)) {
            for (String appId : new String[]{null, "", "abc", "0570", "570\"; limit 500; where uid = \"1", "12345678901", "-1", " 570"}) {
                assertEquals("INVALID_PROVIDER_REQUEST", rig.code(appId), String.valueOf(appId));
            }
            assertTrue(rig.api.received().isEmpty());
            assertTrue(rig.token.received().isEmpty());
        }
    }

    @Test void accessDeniedDropsTheTokenAndTheNextCallFetchesANewOneWithoutRetrying() throws IOException {
        for (int status : new int[]{401, 403}) {
            try (Rig rig = new Rig(Duration.ZERO)) {
                rig.api.enqueue(Reply.json(status, "{\"message\":\"secret echo " + SECRET + "\"}"));
                var error = assertThrows(ProviderException.class, () -> rig.source.details("570"));
                assertEquals("PROVIDER_ACCESS_DENIED", error.code());
                assertFalse(error.getMessage().contains(SECRET));
                assertEquals(1, rig.api.received().size(), "aynı çağrıda yeniden deneme yok");
                rig.token.enqueue(Reply.json(200, TOKEN.replace("tok-1", "tok-2")));
                rig.api.enqueue(Reply.json(200, STEAM_SOURCE), Reply.json(200, EXTERNAL), Reply.json(200, GAME));
                rig.source.details("570");
                assertEquals(2, rig.token.received().size());
            }
        }
    }

    @Test void mapsRateLimitServerAndOtherStatusesToTheRetryPolicyCodes() throws IOException {
        try (Rig rig = new Rig(Duration.ZERO)) {
            rig.api.enqueue(new Reply(429, "{}", 0, null));
            assertEquals("RATE_LIMITED", rig.code("570"));
            rig.api.enqueue(Reply.json(503, "{}"));
            assertEquals("PROVIDER_UNAVAILABLE", rig.code("570"));
            rig.api.enqueue(Reply.json(400, "{}"));
            assertEquals("INVALID_PROVIDER_RESPONSE", rig.code("570"));
        }
    }

    @Test void transportFailureIsAGenericCodeWithoutDetails() throws IOException {
        try (Rig rig = new Rig(Duration.ZERO)) {
            rig.api.close();
            var error = assertThrows(ProviderException.class, () -> rig.source.details("570"));
            assertEquals("PROVIDER_TRANSPORT_ERROR", error.code());
            assertNull(error.getCause());
        }
    }

    @Test void twitchTokenFailuresAreReportedWithoutLeakingTheSecret() throws IOException {
        for (int status : new int[]{400, 401}) {
            try (Rig rig = new Rig(Duration.ZERO)) {
                rig.token.close();
                try (FakeServer twitch = new FakeServer()) {
                    twitch.enqueue(Reply.json(status, "{\"message\":\"invalid client secret " + SECRET + "\"}"));
                    RestClient http = GameRatingConfiguration.restClient(RestClient.builder(), rig.api.baseUrl(), Duration.ofSeconds(1), Duration.ofSeconds(2));
                    var source = new IgdbDetailSource(http, rig.clock, "test-client", SECRET, rig.api.baseUrl() + "/v4", twitch.baseUrl() + "/oauth2/token", Duration.ZERO);
                    var error = assertThrows(ProviderException.class, () -> source.details("570"));
                    assertEquals("PROVIDER_ACCESS_DENIED", error.code());
                    assertFalse(error.getMessage().contains(SECRET));
                    assertTrue(rig.api.received().isEmpty(), "token yokken IGDB'ye istek atılmaz");
                }
            }
        }
    }

    @Test void emptyTokenOrMalformedBodiesAreInvalidResponses() throws IOException {
        try (Rig rig = new Rig(Duration.ZERO)) {
            try (FakeServer badToken = new FakeServer()) {
                badToken.enqueue(Reply.json(200, "{\"access_token\":\"\",\"expires_in\":100}"),
                        Reply.json(200, "{\"access_token\":\"x\",\"expires_in\":0}"), Reply.json(200, "not json"));
                RestClient http = GameRatingConfiguration.restClient(RestClient.builder(), rig.api.baseUrl(), Duration.ofSeconds(1), Duration.ofSeconds(2));
                var source = new IgdbDetailSource(http, rig.clock, "test-client", SECRET, rig.api.baseUrl() + "/v4", badToken.baseUrl() + "/t", Duration.ZERO);
                for (int i = 0; i < 3; i++) {
                    assertEquals("INVALID_PROVIDER_RESPONSE", assertThrows(ProviderException.class, () -> source.details("570")).code());
                }
            }
            rig.api.enqueue(Reply.json(200, "not json"));
            assertEquals("INVALID_PROVIDER_RESPONSE", rig.code("570"));
            rig.api.enqueue(Reply.json(200, "{\"not\":\"an array\"}"));
            assertEquals("INVALID_PROVIDER_RESPONSE", rig.code("570"));
        }
    }

    @Test void providerWithNoUsableFieldsOrAnImpossibleDateIsRejected() throws IOException {
        try (Rig rig = new Rig(Duration.ZERO)) {
            rig.api.enqueue(Reply.json(200, STEAM_SOURCE), Reply.json(200, EXTERNAL), Reply.json(200, "[{\"id\":777,\"summary\":\"  \"}]"));
            assertEquals("INVALID_PROVIDER_RESPONSE", rig.code("570"), "hiç alan yoksa uydurulmaz");
            rig.api.enqueue(Reply.json(200, EXTERNAL), Reply.json(200, "[{\"id\":777,\"summary\":\"ok\",\"first_release_date\":999999999999999}]"));
            assertEquals("INVALID_PROVIDER_RESPONSE", rig.code("570"));
            rig.api.enqueue(Reply.json(200, EXTERNAL), Reply.json(200, "[]"));
            assertEquals("PROVIDER_ITEM_NOT_FOUND", rig.code("570"));
            rig.api.enqueue(Reply.json(200, EXTERNAL), Reply.json(200, "[{\"id\":777,\"summary\":\"" + "x".repeat(16001) + "\"}]"));
            assertEquals("INVALID_PROVIDER_RESPONSE", rig.code("570"), "aşırı uzun metin reddedilir, kırpılmaz");
        }
    }

    @Test void requestsAreSpacedBelowTheProviderRateLimit() throws IOException {
        try (Rig rig = new Rig(Duration.ofMillis(150), true)) {
            long started = System.nanoTime();
            rig.source.details("570");
            long elapsedMillis = Duration.ofNanos(System.nanoTime() - started).toMillis();
            assertTrue(elapsedMillis >= 250, "3 istek arasında 2 aralık (en az ~300 ms) beklenir: " + elapsedMillis);
        }
    }
}
