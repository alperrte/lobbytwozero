package com.ltz.game.infrastructure.client;

import com.ltz.game.PostgresTestSupport;
import com.ltz.game.application.port.GamePriceProvider;
import com.ltz.game.application.port.GamePriceProvider.Market;
import com.ltz.game.application.port.GamePriceProvider.Quote;
import com.ltz.game.application.service.GamePriceService;
import com.ltz.game.infrastructure.client.FakeServer.Reply;
import com.ltz.game.infrastructure.config.GamePriceProperties;
import com.ltz.game.infrastructure.config.GameRatingConfiguration;
import com.ltz.game.infrastructure.persistence.repository.GameExternalReferenceRepository;
import com.ltz.game.infrastructure.persistence.repository.GamePriceRepository;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.client.RestClient;
import org.springframework.web.context.WebApplicationContext;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Gerçek ITAD adaptörü + PostgreSQL/Flyway + public HTTP; yalnız ITAD HTTP yanıtı sahtedir. */
@SpringBootTest(properties={"ltz.game.pricing.sync.enabled=false","ltz.game.rating.sync.enabled=false","ltz.game.pricing.markets="})
@Import(ItadPriceIntegrationTest.Fixture.class)
class ItadPriceIntegrationTest extends PostgresTestSupport {
    static final Market MARKET=new Market("US","USD");
    @TestConfiguration static class Fixture {
        @Bean(destroyMethod="close") FakeServer priceFixture() throws IOException { return new FakeServer(); }
        @Bean @Primary GamePriceProvider prices(FakeServer server,GameExternalReferenceRepository refs,Clock clock) {
            var properties=new GamePriceProperties("fixture-key","https://api.isthereanydeal.com","US","USD",Duration.ofMinutes(15));
            return new ItadPriceClient(GameRatingConfiguration.restClient(RestClient.builder(),server.baseUrl(),Duration.ofSeconds(1),Duration.ofSeconds(2)),properties,refs,clock);
        }
    }
    @Autowired JdbcTemplate jdbc;
    @Autowired FakeServer server;
    @Autowired GamePriceProvider priceProvider;
    @Autowired GamePriceService service;
    @Autowired GamePriceRepository repository;
    @Autowired WebApplicationContext context;
    MockMvc mvc;
    UUID first,second,third,external1,external2,external3;
    @BeforeEach void setup() {
        ((ItadPriceClient)priceProvider).forgetMappingsForTests();
        jdbc.update("delete from game_external_refs");jdbc.update("delete from games");
        jdbc.update("update game_price_sync_lock set owner=null,expires_at=null");
        mvc=MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        first=game("Alpha","570");second=game("Bravo","571");third=game("Charlie","572");
        external1=UUID.randomUUID();external2=UUID.randomUUID();external3=UUID.randomUUID();
    }
    UUID game(String title,String appId) {
        UUID id=UUID.randomUUID();
        jdbc.update("insert into games(id,title,normalized_title,origin,game_type,created_at,updated_at) values (?,?,?,'EXTERNAL','MAIN_GAME',now(),now())",id,title,title.toLowerCase(java.util.Locale.ROOT));
        jdbc.update("insert into game_external_refs(id,game_id,provider,external_id) values (?,?,'STEAM',?)",UUID.randomUUID(),id,appId);return id;
    }
    void offers() {
        server.enqueue(Reply.json(200,"{\"app/570\":\""+external1+"\",\"app/571\":\""+external2+"\",\"app/572\":\""+external3+"\"}"),
                Reply.json(200,"["+row(external1,5,10)+","+row(external2,10,10)+",{\"id\":\""+external3+"\",\"deals\":[]}]"));
        service.refresh();
    }
    String row(UUID id,int price,int regular) {
        return """
                {"id":"%s","deals":[{"shop":{"name":"Steam"},"price":{"amount":%d,"currency":"USD"},
                "regular":{"amount":%d,"currency":"USD"},"cut":%d,"timestamp":"%s","expiry":null,"url":"https://isthereanydeal.com/go/fixture?affiliate=keep"}]}
                """.formatted(id,price,regular,price<regular?50:0,Instant.now().minusSeconds(10));
    }
    @Test void refreshPersistsAndPublicBatchAndDiscountFilterReadOnlyTheLocalMarket() throws Exception {
        offers();int calls=server.received().size();
        mvc.perform(get("/api/v1/game/games/prices").param("gameIds",first.toString(),second.toString(),third.toString()).param("country","US").param("currency","USD"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].status").value("AVAILABLE"))
                .andExpect(jsonPath("$.items[0].offers[0].currentPrice").value(5))
                .andExpect(jsonPath("$.items[0].offers[0].url").value("https://isthereanydeal.com/go/fixture?affiliate=keep"))
                .andExpect(jsonPath("$.items[2].status").value("NO_OFFER"));
        mvc.perform(get("/api/v1/game/games").param("discountedOnly","true"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1)).andExpect(jsonPath("$.items[0].game.gameId").value(first.toString()));
        mvc.perform(get("/api/v1/game/games").param("discountedOnly","true").param("country","DE"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
        service.refresh();assertEquals(calls,server.received().size(),"TTL ve public GET sağlayıcı çağırmaz");
        assertEquals("UNAVAILABLE",service.read(List.of(UUID.randomUUID()),MARKET).items().getFirst().status());
        assertTrue(service.read(List.of(),MARKET).items().isEmpty());
    }
    @Test void dlcGetsPricesToButUnclassifiedDemoAndDelistedGamesAreNotRefreshCandidates() {
        UUID dlc=game("Delta DLC","580"),demo=game("Echo Demo","581"),unknown=game("Foxtrot","582"),delisted=game("Golf","583");
        jdbc.update("update games set game_type='DLC' where id=?",dlc);
        jdbc.update("update games set game_type='DEMO' where id=?",demo);
        jdbc.update("update games set game_type='UNKNOWN' where id=?",unknown);
        jdbc.update("update games set delisted_at=now() where id=?",delisted);
        Set<UUID> candidates=new java.util.HashSet<>();
        repository.candidates(MARKET,Instant.now(),Duration.ofMinutes(20),Duration.ofHours(2),Duration.ofHours(8)).forEach(candidate -> candidates.add(candidate.id()));
        assertTrue(candidates.containsAll(Set.of(first,second,third,dlc)),"ana oyun ve DLC fiyat adayıdır");
        assertFalse(candidates.contains(demo)||candidates.contains(unknown)||candidates.contains(delisted),"demo, sınıfsız ve mağazadan kaldırılmış oyun aday değildir");
        assertEquals(Set.of(first,dlc),repository.tiers(Set.of(first,dlc,demo,unknown,delisted),Duration.ofMinutes(20),Duration.ofHours(2),Duration.ofHours(8)).keySet(),
                "sayfası açılan oyunun kademesi de aynı kuralla");
    }
    @Test void outagePreservesPricesWhileExpiredOffersDoNotMatchTheDiscountFilter() throws Exception {
        offers();
        jdbc.update("update game_price_quotes set fetched_at=now()-interval '1 hour',expires_at=now()-interval '1 minute'");
        assertEquals("STALE",service.read(List.of(first),MARKET).items().getFirst().status());
        mvc.perform(get("/api/v1/game/games").param("discountedOnly","true")).andExpect(jsonPath("$.totalElements").value(0));
        jdbc.update("update game_price_refresh_state set checked_at=now()-interval '3 days'");
        server.enqueue(Reply.json(503,"{}"));service.refresh();
        assertEquals("STALE",service.read(List.of(first),MARKET).items().getFirst().status());
        assertEquals(2,jdbc.queryForObject("select count(*) from game_price_quotes",Integer.class));
        assertEquals(3,jdbc.queryForObject("select count(*) from game_price_refresh_state where status='FAILED'",Integer.class));
    }
    @Test void leaseLossCannotPublishOrClearAnotherWorkersPricesAndConstraintsRejectInvalidMoney() {
        offers();UUID owner=UUID.randomUUID(),other=UUID.randomUUID();Instant now=Instant.now();
        assertTrue(repository.claim(owner,now));assertFalse(repository.claim(other,now));
        int before=server.received().size();service.refresh();assertEquals(before,server.received().size());
        assertFalse(repository.publish(other,Set.of(first),MARKET,List.of(),now));
        repository.failed(other,Set.of(first),MARKET,now,"IGNORED",null);repository.release(other);
        assertEquals(1,repository.quotes(Set.of(first),MARKET).size());
        jdbc.update("update game_price_sync_lock set expires_at=now()-interval '1 second'");
        assertTrue(repository.claim(other,Instant.now()));assertFalse(repository.publish(owner,Set.of(first),MARKET,List.of(),Instant.now()));
        assertTrue(repository.publish(other,Set.of(first),MARKET,List.of(),Instant.now()));repository.release(other);
        assertEquals("NO_OFFER",service.read(List.of(first),MARKET).items().getFirst().status());
        assertThrows(DataAccessException.class,()->jdbc.update("update game_price_quotes set current_price=-1"));
        assertThrows(DataAccessException.class,()->jdbc.update("update game_price_quotes set current_price=regular_price+1"));
        assertThrows(DataAccessException.class,()->jdbc.update("update game_price_quotes set discount_percent=101"));
    }
    @Test void invalidPublicationRollsBackAndInvalidRequestsDoNotBecomeSuccess() throws Exception {
        offers();UUID owner=UUID.randomUUID();repository.claim(owner,Instant.now());
        Quote incomplete=new Quote(first,"Steam",MARKET,BigDecimal.TEN,BigDecimal.ONE,"ITAD",Instant.now(),Instant.now().plusSeconds(60));
        assertThrows(org.springframework.dao.InvalidDataAccessApiUsageException.class,()->repository.publish(owner,Set.of(first),MARKET,List.of(incomplete),Instant.now()));
        assertEquals(1,repository.quotes(Set.of(first),MARKET).size());repository.release(owner);
        for(String[] market:List.of(new String[]{"us","USD"},new String[]{"XX","USD"},new String[]{"US","INVALID"}))
            mvc.perform(get("/api/v1/game/games/prices").param("gameIds",first.toString()).param("country",market[0]).param("currency",market[1])).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/game/games/prices").param("gameIds","bad").param("country","US").param("currency","USD")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/game/games/prices")).andExpect(status().isForbidden());
        assertThrows(IllegalArgumentException.class,()->service.read(null,MARKET));
        var many=new java.util.ArrayList<UUID>();for(int i=0;i<51;i++) many.add(UUID.randomUUID());
        assertThrows(IllegalArgumentException.class,()->service.read(many,MARKET));
        var invalidIds=java.util.Arrays.asList(first,null);
        assertThrows(IllegalArgumentException.class,()->service.read(invalidIds,MARKET));
    }
    @Test void providerBackoffIsPersistentAndInvalidQuoteCannotEscapeItsRequestedGameSet() {
        offers();UUID owner=UUID.randomUUID();repository.claim(owner,Instant.now());
        repository.failed(owner,Set.of(first),MARKET,Instant.now(),"PRICE_RATE_LIMITED",Duration.ofHours(1));repository.release(owner);
        jdbc.update("update game_price_refresh_state set checked_at=now()-interval '1 hour'");
        assertFalse(repository.candidates(MARKET,Instant.now(),Duration.ofMinutes(15),Duration.ofHours(1),Duration.ofHours(6)).stream().anyMatch(candidate -> candidate.id().equals(first)));
        repository.claim(owner,Instant.now());
        Quote wrong=new Quote(UUID.randomUUID(),"Steam",MARKET,BigDecimal.TEN,BigDecimal.ONE,"ITAD",Instant.now(),Instant.now().plusSeconds(60));
        assertThrows(org.springframework.dao.InvalidDataAccessApiUsageException.class,()->repository.publish(owner,Set.of(first),MARKET,List.of(wrong),Instant.now()));
        repository.release(owner);
    }
}
