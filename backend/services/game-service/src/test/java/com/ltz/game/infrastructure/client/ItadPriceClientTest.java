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
    static final Set<UUID> REQUESTED=Set.of(GAME);
    static final Duration VALID_TTL=Duration.ofMinutes(15);
    static final Duration INVALID_TTL=Duration.ofHours(3);
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
    @Test void aViewedGameMayUseTheReservedQuotaShareThatBackgroundRefreshesCannotTouch() throws Exception {
        var refs=mock(GameExternalReferenceRepository.class);
        var game=new Game();game.setId(GAME);
        var ref=new GameExternalReference();ref.setGame(game);ref.setProvider("STEAM");ref.setExternalId("570");
        when(refs.findByGameIdIn(anyCollection())).thenReturn(List.of(ref));
        var props=new GamePriceProperties("fixture-key","https://api.isthereanydeal.com","TR","USD",Duration.ofMinutes(15));
        var clock=Clock.fixed(NOW,ZoneOffset.UTC);
        try(var server=new FakeServer()) {
            server.enqueue(Reply.json(200,mapping()),Reply.json(200,prices("USD",LINK,"\"2026-11-05T12:05:00Z\"")));
            // Pencere payı 2, bunun 1'i ayrılmış: toplu çağrı eşlemeden sonra fiyat isteğinde yerel hız sınırına takılır.
            var background=new ItadPriceClient(GameRatingConfiguration.restClient(RestClient.builder(),server.baseUrl(),Duration.ofSeconds(1),Duration.ofSeconds(1)),
                    props,refs,clock,new ItadRequestLimiter(clock,2,1,Duration.ofMinutes(5),Duration.ZERO,millis -> { }));
            assertEquals(ItadRequestLimiter.LOCAL_THROTTLE,assertThrows(ProviderException.class,()->background.prices(REQUESTED,MARKET)).code());
            assertEquals(1,server.received().size(),"yalnız eşleme isteği gitti, fiyat isteği atılmadı");
        }
        try(var server=new FakeServer()) {
            server.enqueue(Reply.json(200,mapping()),Reply.json(200,prices("USD",LINK,"\"2026-11-05T12:05:00Z\"")));
            var interactive=new ItadPriceClient(GameRatingConfiguration.restClient(RestClient.builder(),server.baseUrl(),Duration.ofSeconds(1),Duration.ofSeconds(1)),
                    props,refs,clock,new ItadRequestLimiter(clock,2,1,Duration.ofMinutes(5),Duration.ZERO,millis -> { }));
            var quotes=interactive.prices(REQUESTED,MARKET,com.ltz.game.application.port.GamePriceProvider.Priority.INTERACTIVE);
            assertEquals(1,quotes.size());
            assertEquals(2,server.received().size(),"etkileşimli çağrı ayrılmış paydan yararlanıp iki isteği de tamamladı");
        }
    }
    @Test void batchesMappingAndPricesPreservesCurrencyAffiliateLinkAndExpiry() throws Exception {
        try(var server=new FakeServer()) {
            server.enqueue(Reply.json(200,mapping()),Reply.json(200,prices("USD",LINK,"\"2026-11-05T12:05:00Z\"")));
            var quote=client(server,"fixture-key").prices(Set.of(GAME),MARKET).getFirst();
            assertEquals(LINK,quote.url());assertEquals(50,quote.discountPercent());assertEquals(MARKET,quote.market());
            assertEquals(NOW.plusSeconds(300),quote.expiresAt());assertEquals(NOW,quote.fetchedAt());
            assertEquals(2,server.received().size());
            assertEquals("fixture-key",server.received().getFirst().apiKey());
            assertEquals("/lookup/id/shop/61/v1",server.received().getFirst().pathAndQuery());
            assertEquals("LTZ-Game-Service/1.0",server.received().getFirst().userAgent());
            assertEquals("[\"app/570\"]",server.received().getFirst().body());
            assertTrue(server.received().getLast().pathAndQuery().contains("country=TR"));
            assertFalse(server.received().getLast().pathAndQuery().contains("capacity"), "mağaza sayısı sınırlanmaz: en ucuz 5 ile yetinilmez");
            assertFalse(server.received().stream().anyMatch(request -> request.pathAndQuery().contains("fixture-key")));
        }
    }
    @Test void disabledAndEmptyInputNeverCallTheProviderAndSecretIsRedacted() throws Exception {
        try(var server=new FakeServer()) {
            var client=client(server,"");assertFalse(client.isConfigured());
            assertEquals(List.of(),client.prices(Set.of(),MARKET));
            assertEquals("PRICE_NOT_CONFIGURED",assertThrows(ProviderException.class,()->client.prices(REQUESTED,MARKET)).code());
            assertTrue(server.received().isEmpty());
            assertFalse(new GamePriceProperties("never-print-me","https://api.isthereanydeal.com","TR","USD",Duration.ofMinutes(15)).toString().contains("never-print-me"));
        }
    }
    @Test void officialShortLinksArePreservedButLookalikesAndCredentialsAreRejected() throws Exception {
        try(var server=new FakeServer()) {
            String url="https://itad.link/018d9386-b9c8-71ea-8724-47f829db21bd/?affiliate=keep-me";
            server.enqueue(Reply.json(200,mapping()),Reply.json(200,prices("USD",url,"null")));
            assertEquals(url,client(server,"fixture").prices(Set.of(GAME),MARKET).getFirst().url());
            for(String invalid:List.of("https://itad.link.evil.invalid/offer","https://itad.link@evil.invalid/offer","http://itad.link/offer")) {
                server.enqueue(Reply.json(200,mapping()),Reply.json(200,prices("USD",invalid,"null")));
                var failingClient1=client(server,"fixture");
                assertEquals("INVALID_PRICE_RESPONSE",assertThrows(ProviderException.class,
                        ()->failingClient1.prices(REQUESTED,MARKET)).code());
            }
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
                var failingClient2=client(server,"fixture");
                assertEquals("INVALID_PRICE_RESPONSE",assertThrows(ProviderException.class,()->failingClient2.prices(REQUESTED,MARKET)).code());
            }
        }
    }
    @Test void wrongCurrencyUnsafeUrlExpiredAndMissingPriceRowsNeverBecomeValidPrices() throws Exception {
        try(var server=new FakeServer()) {
            for(String body:List.of(prices("EUR",LINK,"null"),prices("USD","http://127.0.0.1/private","null"),
                    "{}","null","[null]")) {
                server.enqueue(Reply.json(200,mapping()),Reply.json(200,body));
                var failingClient3=client(server,"fixture");
                assertThrows(ProviderException.class,()->failingClient3.prices(REQUESTED,MARKET));
            }
        }
    }
    @Test void gamesWithoutAnyPriceRowAreSimplyWithoutOffersAndNeverInvalidateTheRestOfTheBatch() throws Exception {
        try(var server=new FakeServer()) {
            // ITAD fiyatı olmayan oyun için satır döndürmez: boş yanıt geçerlidir ve teklif üretmez.
            server.enqueue(Reply.json(200,mapping()),Reply.json(200,"[]"));
            assertEquals(List.of(),client(server,"fixture").prices(REQUESTED,MARKET));
        }
    }
    private static String deal(String store,int price,int regular,String expiry) {
        return "{\"shop\":{\"name\":\""+store+"\"},\"price\":{\"amount\":"+price+",\"currency\":\"USD\"},\"regular\":{\"amount\":"+regular
                +",\"currency\":\"USD\"},\"cut\":0,\"timestamp\":\"2026-11-05T11:59:00Z\",\"expiry\":"+expiry+",\"url\":\""+LINK+"\"}";
    }
    private static String row(UUID id,String... deals) { return "{\"id\":\""+id+"\",\"deals\":["+String.join(",",deals)+"]}"; }
    @Test void aLearnedSteamToItadMappingIsNotLookedUpAgainSoARefreshIsOneRequest() throws Exception {
        try(var server=new FakeServer()) {
            server.enqueue(Reply.json(200,mapping()),Reply.json(200,prices("USD",LINK,"null")),Reply.json(200,prices("USD",LINK,"null")));
            var client=client(server,"fixture");
            assertEquals(1,client.prices(REQUESTED,MARKET).size());
            assertEquals(2,server.received().size(),"ilk tur: eşleme + fiyat");
            assertEquals(1,client.prices(REQUESTED,MARKET).size());
            assertEquals(3,server.received().size(),"ikinci tur yalnız fiyat isteği atar");
            assertTrue(server.received().getLast().pathAndQuery().startsWith("/games/prices/v3"));
        }
    }
    @Test void everyStoreOfAGameIsReturnedNotJustTheCheapestFive() throws Exception {
        try(var server=new FakeServer()) {
            String[] stores={"Steam","GOG","Humble Store","Epic Game Store","Fanatical","GreenManGaming","GamersGate","IndieGala"};
            String[] deals=new String[stores.length];
            for(int i=0;i<stores.length;i++) deals[i]=deal(stores[i],5+i,20,"null");
            server.enqueue(Reply.json(200,mapping()),Reply.json(200,"["+row(ITAD,deals)+"]"));
            var quotes=client(server,"fixture").prices(REQUESTED,MARKET);
            assertEquals(8,quotes.size(),"altı ve üzeri teklif de alınır (Steam dahil)");
            assertTrue(quotes.stream().anyMatch(quote -> quote.store().equals("Steam")));
        }
    }
    @Test void anAbsurdNumberOfDealsForOneGameIsRejectedAsAMalformedResponse() throws Exception {
        try(var server=new FakeServer()) {
            String[] deals=new String[ItadPriceMapper.MAX_DEALS+1];
            for(int i=0;i<deals.length;i++) deals[i]=deal("Store "+i,5,10,"null");
            server.enqueue(Reply.json(200,mapping()),Reply.json(200,"["+row(ITAD,deals)+"]"));
            var failing=client(server,"fixture");
            assertEquals("INVALID_PRICE_RESPONSE",assertThrows(ProviderException.class,()->failing.prices(REQUESTED,MARKET)).code());
            String[] limit=new String[ItadPriceMapper.MAX_DEALS];
            for(int i=0;i<limit.length;i++) limit[i]=deal("Store "+i,5,10,"null");
            server.enqueue(Reply.json(200,mapping()),Reply.json(200,"["+row(ITAD,limit)+"]"));
            assertEquals(ItadPriceMapper.MAX_DEALS,client(server,"fixture").prices(REQUESTED,MARKET).size(),"sınırdaki yanıt kabul edilir");
        }
    }
    @Test void aBodyOverTheSixteenMebibyteLimitIsRejected() throws Exception {
        try(var server=new FakeServer()) {
            String padding="x".repeat(16_777_300);
            server.enqueue(Reply.json(200,mapping()),Reply.json(200,"[{\"id\":\""+ITAD+"\",\"deals\":[],\"pad\":\""+padding+"\"}]"));
            var failing=client(server,"fixture");
            assertEquals("INVALID_PRICE_RESPONSE",assertThrows(ProviderException.class,()->failing.prices(REQUESTED,MARKET)).code());
        }
    }
    @Test void twoDealsFromTheSameStoreKeepTheLowestPriceAndDoNotInvalidateTheGame() throws Exception {
        try(var server=new FakeServer()) {
            server.enqueue(Reply.json(200,mapping()),Reply.json(200,"["+row(ITAD,deal("Steam",15,15,"null"),deal("Humble Store",10,10,"null"),deal("Steam",7,8,"null"))+"]"));
            var quotes=client(server,"fixture").prices(REQUESTED,MARKET);
            assertEquals(2,quotes.size());
            var steam=quotes.stream().filter(quote -> quote.store().equals("Steam")).findFirst().orElseThrow();
            assertEquals(0,steam.currentPrice().compareTo(new java.math.BigDecimal("7")), "mağaza başına en düşük fiyat");
            assertEquals(1,quotes.stream().filter(quote -> quote.store().equals("Steam")).count());
        }
    }
    @Test void anExpiredDealIsSkippedWhileValidDealsOfTheSameGameAndOtherGamesSurvive() throws Exception {
        UUID otherGame=UUID.randomUUID(),otherItad=UUID.randomUUID();
        var refs=mock(GameExternalReferenceRepository.class);
        var first=new Game();first.setId(GAME);var second=new Game();second.setId(otherGame);
        var refOne=new GameExternalReference();refOne.setGame(first);refOne.setProvider("STEAM");refOne.setExternalId("570");
        var refTwo=new GameExternalReference();refTwo.setGame(second);refTwo.setProvider("STEAM");refTwo.setExternalId("730");
        when(refs.findByGameIdIn(anyCollection())).thenReturn(List.of(refOne,refTwo));
        try(var server=new FakeServer()) {
            server.enqueue(Reply.json(200,"{\"app/570\":\""+ITAD+"\",\"app/730\":\""+otherItad+"\"}"),
                    Reply.json(200,"["+row(ITAD,deal("Steam",5,10,"\"2026-11-05T11:00:00Z\""),deal("GOG",6,10,"null"))+","+row(otherItad,deal("Steam",3,3,"null"))+"]"));
            var quotes=client(server,"fixture",refs).prices(Set.of(GAME,otherGame),MARKET);
            assertEquals(2,quotes.size(),"süresi geçmiş Steam teklifi atlanır, GOG ve diğer oyun kalır");
            assertTrue(quotes.stream().noneMatch(quote -> quote.gameId().equals(GAME) && quote.store().equals("Steam")));
            assertTrue(quotes.stream().anyMatch(quote -> quote.gameId().equals(otherGame)));
        }
    }
    @Test void aSingleExpiredDealYieldsNoOfferInsteadOfAFailure() throws Exception {
        try(var server=new FakeServer()) {
            server.enqueue(Reply.json(200,mapping()),Reply.json(200,prices("USD",LINK,"\"2026-11-05T11:00:00Z\"")));
            assertEquals(List.of(),client(server,"fixture").prices(REQUESTED,MARKET));
        }
    }
    @Test void upToTwoHundredGamesAreAcceptedAndMoreAreRejected() throws Exception {
        var many=new java.util.HashSet<UUID>();
        for(int index=0;index<201;index++) many.add(UUID.randomUUID());
        try(var server=new FakeServer()) {
            var client=client(server,"fixture");
            assertThrows(IllegalArgumentException.class,()->client.prices(many,MARKET));
            assertTrue(server.received().isEmpty());
        }
    }
    @Test void accessDeniedRateLimitServerErrorAndBadJsonAreBoundedAndNeverRetried() throws Exception {
        try(var server=new FakeServer()) {
            for(int status:new int[]{401,403,429,500}) {
                int before=server.received().size();server.enqueue(Reply.json(status,"{\"secret\":\"fixture-key\"}"));
                var failingClient4=client(server,"fixture-key");
                var error=assertThrows(ProviderException.class,()->failingClient4.prices(REQUESTED,MARKET));
                assertFalse(error.getMessage().contains("fixture-key"));assertEquals(before+1,server.received().size());
            }
            server.enqueue(Reply.json(200,"not-json"));
            var failingClient5=client(server,"fixture");
            assertThrows(ProviderException.class,()->failingClient5.prices(REQUESTED,MARKET));
        }
    }
    private static List<String> capturedTelemetry(Runnable action) {
        var logger=(ch.qos.logback.classic.Logger)org.slf4j.LoggerFactory.getLogger(ItadTelemetry.class);
        var appender=new ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>();
        var previous=logger.getLevel();logger.setLevel(ch.qos.logback.classic.Level.INFO);
        appender.start();logger.addAppender(appender);
        try { action.run(); }
        finally { logger.detachAppender(appender);logger.setLevel(previous); }
        return appender.list.stream().map(ch.qos.logback.classic.spi.ILoggingEvent::getFormattedMessage).toList();
    }
    @Test void aRateLimitFromItadIsLoggedWithTheWindowFillAndNeverLeaksTheKeyTheUrlOrTheBody() throws Exception {
        try(var server=new FakeServer()) {
            server.retryAfter("120").enqueue(Reply.json(200,mapping()),Reply.json(429,"{\"secret\":\"fixture-key\"}"));
            var limited=client(server,"fixture-key");
            var lines=capturedTelemetry(()->assertThrows(ProviderException.class,()->limited.prices(REQUESTED,MARKET)));
            var rateLimit=lines.stream().filter(line -> line.contains("olay=429")).toList();
            assertEquals(1,rateLimit.size(),lines.toString());
            var line=rateLimit.getFirst();
            assertTrue(line.contains("yol=prices") && line.contains("pazar=TR-USD") && line.contains("oncelik=BACKGROUND"),line);
            assertTrue(line.contains("pencere_ici=2") && line.contains("son60sn=2") && line.contains("limit=850") && line.contains("rezerv=150"),line);
            assertTrue(line.contains("etkin_once=850") && line.contains("etkin_sonra=850"),"gözlenen 2 istek etkin limitin yarısından çok az: limit düşmez, yalnız Retry-After bloğu geçerli: "+line);
            assertTrue(line.contains("retry_after_ham=\"120\"") && line.contains("engel_sn=120") && line.contains("ardisik=1"),line);
            var all=String.join("\n",lines);
            assertFalse(all.contains("fixture-key"));assertFalse(all.contains("isthereanydeal"));assertFalse(all.contains(server.baseUrl()));assertFalse(all.contains("secret"));
        }
    }
    @Test void interactiveCallsAreLabelledAsSuchInTheRateLimitLog() throws Exception {
        try(var server=new FakeServer()) {
            server.enqueue(Reply.json(200,mapping()),Reply.json(429,"{}"));
            var limited=client(server,"fixture-key");
            var lines=capturedTelemetry(()->assertThrows(ProviderException.class,()->limited.prices(REQUESTED,MARKET,com.ltz.game.application.port.GamePriceProvider.Priority.INTERACTIVE)));
            assertTrue(lines.stream().anyMatch(line -> line.contains("olay=429") && line.contains("oncelik=INTERACTIVE")),lines.toString());
        }
    }
    @Test void rateLimitHeadersOnASuccessfulResponseAreObservedOnceAndNeverChangeTheResult() throws Exception {
        try(var server=new FakeServer()) {
            server.header("X-RateLimit-Remaining","42").enqueue(Reply.json(200,mapping()),Reply.json(200,prices("USD",LINK,"\"2026-11-05T12:05:00Z\"")));
            var observed=client(server,"fixture-key");
            var result=new java.util.ArrayList<com.ltz.game.application.port.GamePriceProvider.Quote>();
            var lines=capturedTelemetry(()->result.addAll(observed.prices(REQUESTED,MARKET)));
            assertEquals(1,result.size());
            assertEquals(1,lines.stream().filter(line -> line.contains("olay=baslik") && line.contains("x-ratelimit-remaining=\"42\"")).count(),lines.toString());
        }
    }
    @Test void aRateLimitBlocksFurtherRequestsWithTheBlockCodeWithoutNetworkCallsUntilRetryAfterPasses() throws Exception {
        var now=new java.util.concurrent.atomic.AtomicReference<>(NOW);
        Clock moving=new Clock() {
            @Override public java.time.ZoneId getZone() { return ZoneOffset.UTC; }
            @Override public Clock withZone(java.time.ZoneId zone) { return this; }
            @Override public Instant instant() { return now.get(); }
        };
        var refs=mock(GameExternalReferenceRepository.class);
        var game=new Game();game.setId(GAME);
        var ref=new GameExternalReference();ref.setGame(game);ref.setProvider("STEAM");ref.setExternalId("570");
        when(refs.findByGameIdIn(anyCollection())).thenReturn(List.of(ref));
        var props=new GamePriceProperties("fixture-key","https://api.isthereanydeal.com","TR","USD",VALID_TTL);
        try(var server=new FakeServer()) {
            server.retryAfter("60").enqueue(Reply.json(200,mapping()),Reply.json(429,"{}"),Reply.json(200,prices("USD",LINK,"\"2026-11-05T12:05:00Z\"")));
            var limited=new ItadPriceClient(GameRatingConfiguration.restClient(RestClient.builder(),server.baseUrl(),Duration.ofSeconds(1),Duration.ofSeconds(1)),props,refs,moving);
            assertFalse(limited.isBlocked());
            assertEquals(Duration.ZERO,limited.blockedFor());
            assertEquals("PRICE_RATE_LIMITED",assertThrows(ProviderException.class,()->limited.prices(REQUESTED,MARKET)).code());
            assertTrue(limited.isBlocked());
            assertEquals(Duration.ofSeconds(60),limited.blockedFor());
            int before=server.received().size();
            var blocked=assertThrows(ProviderException.class,()->limited.prices(REQUESTED,MARKET));
            assertEquals(ItadPriceClient.PROVIDER_BLOCKED,blocked.code(),"yerel pencere doluluğundan (PRICE_LOCAL_THROTTLE) ayrı kod");
            assertEquals(Duration.ofSeconds(60),blocked.retryAfter());
            assertEquals(before,server.received().size(),"blok sırasında ağa istek çıkmaz");
            now.set(NOW.plusSeconds(61));
            assertFalse(limited.isBlocked());
            assertEquals(Duration.ZERO,limited.blockedFor());
            assertEquals(1,limited.prices(REQUESTED,MARKET).size(),"süre dolunca istek yeniden gider");
            assertEquals(before+1,server.received().size());
        }
    }
    @Test void aGameItadCannotMapIsNotLookedUpAgainUntilTheNegativeCacheExpires() throws Exception {
        var now=new java.util.concurrent.atomic.AtomicReference<>(NOW);
        Clock moving=new Clock() {
            @Override public java.time.ZoneId getZone() { return ZoneOffset.UTC; }
            @Override public Clock withZone(java.time.ZoneId zone) { return this; }
            @Override public Instant instant() { return now.get(); }
        };
        var refs=mock(GameExternalReferenceRepository.class);
        var game=new Game();game.setId(GAME);
        var ref=new GameExternalReference();ref.setGame(game);ref.setProvider("STEAM");ref.setExternalId("570");
        when(refs.findByGameIdIn(anyCollection())).thenReturn(List.of(ref));
        var props=new GamePriceProperties("fixture-key","https://api.isthereanydeal.com","TR","USD",VALID_TTL);
        try(var server=new FakeServer()) {
            server.enqueue(Reply.json(200,"{\"app/570\":null}"),Reply.json(200,"{\"app/570\":null}"));
            var unmapped=new ItadPriceClient(GameRatingConfiguration.restClient(RestClient.builder(),server.baseUrl(),Duration.ofSeconds(1),Duration.ofSeconds(1)),props,refs,moving);
            assertEquals(List.of(),unmapped.prices(REQUESTED,MARKET));
            assertEquals(1,server.received().size(),"eşleme yoksa fiyat isteği de atılmaz");
            assertEquals(List.of(),unmapped.prices(REQUESTED,MARKET));
            assertEquals(1,server.received().size(),"eşlemesiz oyun için ikinci lookup atılmaz");
            now.set(NOW.plus(ItadPriceClient.UNMAPPED_TTL).minusSeconds(1));
            assertEquals(List.of(),unmapped.prices(REQUESTED,MARKET));
            assertEquals(1,server.received().size(),"süre dolmadan yeniden sorulmaz");
            now.set(NOW.plus(ItadPriceClient.UNMAPPED_TTL).plusSeconds(1));
            assertEquals(List.of(),unmapped.prices(REQUESTED,MARKET));
            assertEquals(2,server.received().size(),"süre dolunca yeniden sorulur");
        }
    }
    @Test void providerRetryAfterAndStartupValidationAreExplicit() throws Exception {
        try(var server=new FakeServer()) {
            for(String value:List.of("3600","Thu, 5 Nov 2026 14:00:00 GMT","invalid")) {
                server.retryAfter(value).enqueue(Reply.json(429,"{}"));
                var failingClient7=client(server,"fixture");
                var error=assertThrows(ProviderException.class,()->failingClient7.prices(REQUESTED,MARKET));
                assertEquals(value.equals("3600")?Duration.ofHours(1):value.startsWith("Thu")?Duration.ofHours(2):Duration.ofMinutes(15),error.retryAfter());
            }
        }
        assertThrows(IllegalArgumentException.class,()->new GamePriceProperties("key","http://insecure.invalid","TR","USD",VALID_TTL));
        assertThrows(IllegalArgumentException.class,()->new GamePriceProperties("key","https://user@api.example.invalid","TR","USD",VALID_TTL));
        assertThrows(IllegalArgumentException.class,()->new GamePriceProperties("key","https://api.example.invalid","TR","USD",Duration.ZERO));
        assertThrows(IllegalArgumentException.class,()->new GamePriceProperties("key","https://api.example.invalid","TR","USD",INVALID_TTL));
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
            var failingClient8=client(server,"fixture",refs);
            assertEquals("AMBIGUOUS_PRICE_MAPPING",assertThrows(ProviderException.class,()->failingClient8.prices(REQUESTED,MARKET)).code());
        }
    }
    @Test void transportTimeoutIsControlledWithoutRetry() throws Exception {
        try(var server=new FakeServer()) {
            server.enqueue(Reply.slow(1500));
            var failingClient9=client(server,"fixture");
            assertEquals("PRICE_UNAVAILABLE",assertThrows(ProviderException.class,()->failingClient9.prices(REQUESTED,MARKET)).code());
            assertEquals(1,server.received().size());
        }
    }
}
