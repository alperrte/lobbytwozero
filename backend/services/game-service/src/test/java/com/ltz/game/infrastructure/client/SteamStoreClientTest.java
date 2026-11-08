package com.ltz.game.infrastructure.client;

import com.ltz.game.application.port.GameProvider;
import com.ltz.game.domain.enums.LanguageSupportStatus;
import com.ltz.game.domain.exception.ProviderException;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class SteamStoreClientTest {
    static final String HASH = "ce4d5e53b36cb9d3c4309d1df72bf8663bbbc7ef";
    MockRestServiceServer server;
    SteamStoreClient client;
    final List<Long> sleeps = new java.util.ArrayList<>();

    @BeforeEach void setup() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://store.steampowered.com");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new SteamStoreClient(builder.build(), Duration.ofMillis(1700), sleeps::add);
    }

    private static String body(String app, String data) { return "{\"" + app + "\":{\"success\":true,\"data\":" + data + "}}"; }

    @Test void parsesCoverDevelopersPublishersAndLanguagesAndNormalizesTheHeaderAddress() {
        server.expect(method(HttpMethod.GET)).andExpect(request -> {
            assertEquals("store.steampowered.com", request.getURI().getHost());
            assertEquals("/api/appdetails", request.getURI().getPath());
            assertTrue(request.getURI().getQuery().contains("appids=3595230") && request.getURI().getQuery().contains("l=english"));
        }).andRespond(withSuccess(body("3595230", """
                {"type":"game","header_image":"https://shared.akamai.steamstatic.com/store_item_assets/steam/apps/3595230/%s/header.jpg?t=1778886532",
                 "developers":["Infinity Ward"," Treyarch ",""],"publishers":["Activision"],
                 "supported_languages":"English<strong>*</strong>, French<strong>*</strong>, Turkish, Klingon, Spanish - Spain<strong>*</strong><br><strong>*</strong>languages with full audio support"}
                """.formatted(HASH)), MediaType.APPLICATION_JSON));
        var details = client.details("3595230").orElseThrow();
        assertEquals("https://shared.fastly.steamstatic.com/store_item_assets/steam/apps/3595230/" + HASH + "/header.jpg", details.headerImageUrl(),
                "yeni uygulamalarda içerik özeti yolda kalır; sunucu sabit, zaman damgası atılır");
        assertEquals(Set.of("Infinity Ward", "Treyarch"), details.developers());
        assertEquals(Set.of("Activision"), details.publishers());
        assertEquals(List.of("en-us", "fr-fr", "tr-tr", "es-es"), details.languages().stream().map(GameProvider.LanguageSupport::languageCode).toList(),
                "bilinmeyen dil (Klingon) uydurulmaz, atlanır");
        var english = details.languages().getFirst();
        assertEquals(LanguageSupportStatus.SUPPORTED, english.interfaceSupport());
        assertEquals(LanguageSupportStatus.SUPPORTED, english.audioSupport(), "* işareti tam ses desteği");
        var turkish = details.languages().get(2);
        assertEquals(LanguageSupportStatus.UNKNOWN, turkish.audioSupport(), "işaret yoksa ses UNKNOWN (UNSUPPORTED iddiası yok)");
        assertEquals(LanguageSupportStatus.UNKNOWN, turkish.subtitleSupport());
    }

    @Test void dlcCarriesTheFullGameAppIdAndUnsafeValuesAreDropped() {
        server.expect(method(HttpMethod.GET)).andRespond(withSuccess(body("77", """
                {"type":"dlc","header_image":"https://evil.example/header.jpg","fullgame":{"appid":"570","name":"Dota 2"},
                 "developers":"not-an-array","supported_languages":"English"}
                """), MediaType.APPLICATION_JSON));
        var details = client.details("77").orElseThrow();
        assertEquals("570", details.fullGameAppId());
        assertEquals(null, details.headerImageUrl(), "tanınmayan sunucudan görsel adresi kabul edilmez");
        assertTrue(details.developers().isEmpty());
    }

    @Test void unavailableAppsReturnEmptyAndFailuresAreBoundedProviderErrors() {
        server.expect(method(HttpMethod.GET)).andRespond(withSuccess("{\"99\":{\"success\":false}}", MediaType.APPLICATION_JSON));
        assertTrue(client.details("99").isEmpty());
        server.reset();
        server.expect(method(HttpMethod.GET)).andRespond(withStatus(org.springframework.http.HttpStatus.TOO_MANY_REQUESTS));
        var limited = assertThrows(ProviderException.class, () -> client.details("1"));
        assertEquals("RATE_LIMITED", limited.code());
        assertEquals(Duration.ofMinutes(5), limited.retryAfter());
        server.reset();
        server.expect(method(HttpMethod.GET)).andRespond(withSuccess("{\"2\":{\"data\":{}}}", MediaType.APPLICATION_JSON));
        assertEquals("INVALID_PROVIDER_RESPONSE", assertThrows(ProviderException.class, () -> client.details("2")).code());
        server.reset();
        server.expect(method(HttpMethod.GET)).andRespond(withSuccess("not json", MediaType.APPLICATION_JSON));
        assertEquals("INVALID_PROVIDER_RESPONSE", assertThrows(ProviderException.class, () -> client.details("3")).code());
        assertEquals("INVALID_PROVIDER_REQUEST", assertThrows(ProviderException.class, () -> client.details("0; drop")).code());
    }

    @Test void requestsAreSpacedByTheMinimumInterval() {
        server.expect(method(HttpMethod.GET)).andRespond(withSuccess("{\"1\":{\"success\":false}}", MediaType.APPLICATION_JSON));
        server.expect(method(HttpMethod.GET)).andRespond(withSuccess("{\"2\":{\"success\":false}}", MediaType.APPLICATION_JSON));
        client.details("1");
        client.details("2");
        assertTrue(sleeps.size() >= 1 && sleeps.getLast() > 1000 && sleeps.getLast() <= 1700, "ikinci istek en az ~1,7 sn bekler: " + sleeps);
    }
}
