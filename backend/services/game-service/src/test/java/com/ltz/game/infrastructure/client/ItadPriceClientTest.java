package com.ltz.game.infrastructure.client;

import com.ltz.game.application.port.GamePriceProvider.Market;
import com.ltz.game.domain.entity.Game;
import com.ltz.game.domain.entity.GameExternalReference;
import com.ltz.game.domain.exception.ProviderException;
import com.ltz.game.infrastructure.client.FakeServer.Reply;
import com.ltz.game.infrastructure.config.GamePriceProperties;
import com.ltz.game.infrastructure.config.GameRatingConfiguration;
import com.ltz.game.infrastructure.persistence.repository.GameExternalReferenceRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ItadPriceClientTest {
    static final UUID GAME=UUID.randomUUID(), ITAD=UUID.randomUUID();
    static final Instant NOW=Instant.parse("2026-11-05T12:00:00Z");
    static final Market MARKET=new Market("TR","USD");
    static final String LINK="https://isthereanydeal.com/go/fixture?affiliate=keep-me";
    static String mapping() { return "{\"app/570\":\""+ITAD+"\"}"; }
    static String prices(String currency,String url,String expiry) {
        return """
                [{"id":"%s","deals":[{"shop":{"name":"Steam"},"price":{"amount":5,"currency":"%s"},
                "regular":{"amount":10,"currency":"%s"},"cut":50,"timestamp":"2026-11-05T11:59:00Z","expiry":%s,"url":"%s"}]}]
                """.formatted(ITAD,currency,currency,expiry,url);
    }
    static ItadPriceClient client(FakeServer server,String key) {
        var refs=mock(GameExternalReferenceRepository.class);
        var game=new Game();game.setId(GAME);
        var ref=new GameExternalReference();ref.setGame(game);ref.setProvider("STEAM");ref.setExternalId("570");
        when(refs.findByGameIdIn(anyCollection())).thenReturn(List.of(ref));
        return client(server,key,refs);
    }
    static ItadPriceClient client(FakeServer server,String key,GameExternalReferenceRepository refs) {
        var props=new GamePriceProperties(key,"https://api.isthereanydeal.com","TR","USD",Duration.ofMinutes(15));
        return new ItadPriceClient(GameRatingConfiguration.restClient(RestClient.builder(),server.baseUrl(),Duration.ofSeconds(1),Duration.ofSeconds(1)),
                props,refs,Clock.fixed(NOW,ZoneOffset.UTC));
    }
    @Test void batchesMappingAndPricesPreservesCurrencyAffiliateLinkAndExpiry() throws Exception {
        try(var server=new FakeServer()) {
            server.enqueue(Reply.json(200,mapping()),Reply.json(200,prices("USD",LINK,"\"2026-11-05T12:05:00Z\"")));
            var quote=client(server,"fixture-key").prices(Set.of(GAME),MARKET).getFirst();
            assertEquals(LINK,quote.url());assertEquals(50,quote.discountPercent());assertEquals(MARKET,quote.market());
            assertEquals(NOW.plusSeconds(300),quote.expiresAt());assertEquals(NOW,quote.fetchedAt());
            assertEquals(2,server.received().size());
            assertEquals("fixture-key",server.received().getFirst().apiKey());
            assertEquals("/lookup/shop/61/id/v1",server.received().getFirst().pathAndQuery());
            assertEquals("[\"app/570\"]",server.received().getFirst().body());
            assertTrue(server.received().getLast().pathAndQuery().contains("country=TR"));
            assertFalse(server.received().stream().anyMatch(request -> request.pathAndQuery().contains("fixture-key")));
        }
    }
    @Test void disabledAndEmptyInputNeverCallTheProviderAndSecretIsRedacted() throws Exception {
        try(var server=new FakeServer()) {
            var client=client(server,"");assertFalse(client.isConfigured());
            assertEquals(List.of(),client.prices(Set.of(),MARKET));
            assertEquals("PRICE_NOT_CONFIGURED",assertThrows(ProviderException.class,()->client.prices(Set.of(GAME),MARKET)).code());
            assertTrue(server.received().isEmpty());
            assertFalse(new GamePriceProperties("never-print-me","https://api.isthereanydeal.com","TR","USD",Duration.ofMinutes(15)).toString().contains("never-print-me"));
        }
    }
    @Test void explicitUnmappedGameAndEmptyOffersAreDifferentFromMalformedResponses() throws Exception {
        try(var server=new FakeServer()) {
            server.enqueue(Reply.json(200,"{\"app/570\":null}"));
            assertTrue(client(server,"fixture").prices(Set.of(GAME),MARKET).isEmpty());
            server.enqueue(Reply.json(200,mapping()),Reply.json(200,"[{\"id\":\""+ITAD+"\",\"deals\":[]}]"));
            assertTrue(client(server,"fixture").prices(Set.of(GAME),MARKET).isEmpty());
            for(String body:List.of("{}","[]","{\"app/570\":false}")) {
                server.enqueue(Reply.json(200,body));
                assertEquals("INVALID_PRICE_RESPONSE",assertThrows(ProviderException.class,()->client(server,"fixture").prices(Set.of(GAME),MARKET)).code());
            }
        }
    }
    @Test void wrongCurrencyUnsafeUrlExpiredAndMissingPriceRowsNeverBecomeValidPrices() throws Exception {
        try(var server=new FakeServer()) {
            for(String body:List.of(prices("EUR",LINK,"null"),prices("USD","http://127.0.0.1/private","null"),
                    prices("USD",LINK,"\"2026-11-05T11:00:00Z\""),"[]","{}","null","[null]")) {
                server.enqueue(Reply.json(200,mapping()),Reply.json(200,body));
                assertThrows(ProviderException.class,()->client(server,"fixture").prices(Set.of(GAME),MARKET));
            }
        }
    }
    @Test void accessDeniedRateLimitServerErrorAndBadJsonAreBoundedAndNeverRetried() throws Exception {
        try(var server=new FakeServer()) {
            for(int status:new int[]{401,403,429,500}) {
                int before=server.received().size();server.enqueue(Reply.json(status,"{\"secret\":\"fixture-key\"}"));
                var error=assertThrows(ProviderException.class,()->client(server,"fixture-key").prices(Set.of(GAME),MARKET));
                assertFalse(error.getMessage().contains("fixture-key"));assertEquals(before+1,server.received().size());
            }
            server.enqueue(Reply.json(200,"not-json"));
            assertThrows(ProviderException.class,()->client(server,"fixture").prices(Set.of(GAME),MARKET));
            Set<UUID> many=new java.util.HashSet<>();for(int i=0;i<51;i++) many.add(UUID.randomUUID());
            assertThrows(IllegalArgumentException.class,()->client(server,"fixture").prices(many,MARKET));
        }
    }
    @Test void providerRetryAfterAndStartupValidationAreExplicit() throws Exception {
        try(var server=new FakeServer()) {
            for(String value:List.of("3600","Thu, 5 Nov 2026 14:00:00 GMT","invalid")) {
                server.retryAfter(value).enqueue(Reply.json(429,"{}"));
                var error=assertThrows(ProviderException.class,()->client(server,"fixture").prices(Set.of(GAME),MARKET));
                assertEquals(value.equals("3600")?Duration.ofHours(1):value.startsWith("Thu")?Duration.ofHours(2):Duration.ofMinutes(15),error.retryAfter());
            }
        }
        assertThrows(IllegalArgumentException.class,()->new GamePriceProperties("key","http://insecure.invalid","TR","USD",Duration.ofMinutes(15)));
        assertThrows(IllegalArgumentException.class,()->new GamePriceProperties("key","https://user@api.example.invalid","TR","USD",Duration.ofMinutes(15)));
        assertThrows(IllegalArgumentException.class,()->new GamePriceProperties("key","https://api.example.invalid","TR","USD",Duration.ZERO));
        assertThrows(IllegalArgumentException.class,()->new GamePriceProperties("key","https://api.example.invalid","TR","USD",Duration.ofHours(2)));
    }
    @Test void verifiedSteamAliasesAreDeduplicatedButAmbiguousEditionPricesAreRejected() throws Exception {
        var refs=mock(GameExternalReferenceRepository.class);var game=new Game();game.setId(GAME);
        var aliases=new java.util.ArrayList<GameExternalReference>();
        for(String app:List.of("570","571")) {
            var ref=new GameExternalReference();ref.setProvider("STEAM");ref.setExternalId(app);ref.setGame(game);aliases.add(ref);
        }
        when(refs.findByGameIdIn(anyCollection())).thenReturn(aliases);
        try(var server=new FakeServer()) {
            server.enqueue(Reply.json(200,"{\"app/570\":\""+ITAD+"\",\"app/571\":\""+ITAD+"\"}"),Reply.json(200,prices("USD",LINK,"null")));
            assertEquals(1,client(server,"fixture",refs).prices(Set.of(GAME),MARKET).size());
            server.enqueue(Reply.json(200,"{\"app/570\":\""+ITAD+"\",\"app/571\":\""+UUID.randomUUID()+"\"}"));
            assertEquals("AMBIGUOUS_PRICE_MAPPING",assertThrows(ProviderException.class,()->client(server,"fixture",refs).prices(Set.of(GAME),MARKET)).code());
        }
    }
    @Test void transportTimeoutIsControlledWithoutRetry() throws Exception {
        try(var server=new FakeServer()) {
            server.enqueue(Reply.slow(1500));
            assertEquals("PRICE_UNAVAILABLE",assertThrows(ProviderException.class,()->client(server,"fixture").prices(Set.of(GAME),MARKET)).code());
            assertEquals(1,server.received().size());
        }
    }
}
