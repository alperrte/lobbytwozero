package com.ltz.game.api.controller;

import com.ltz.game.PostgresTestSupport;
import com.ltz.game.application.service.CatalogImportService;
import com.ltz.game.application.service.DiscoveryQuery;
import com.ltz.game.application.service.EditorialCatalogService;
import com.ltz.game.application.service.GameDiscoveryService;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class GameDiscoveryControllerTest extends PostgresTestSupport {
    @Autowired WebApplicationContext context;
    @Autowired JdbcTemplate jdbc;
    @Autowired GameDiscoveryService discovery;
    @Autowired EditorialCatalogService editorial;
    MockMvc mvc;
    @BeforeEach void setup() {
        mvc=MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        jdbc.update("delete from game_external_refs"); jdbc.update("delete from games");
    }
    UUID game(String title,String origin,String type,String status) {
        UUID id=UUID.randomUUID();
        jdbc.update("insert into games(id,title,normalized_title,created_at,updated_at,origin,game_type,status,metadata_completeness) values (?,?,?,now(),now(),?,?,?,'INDEX_ONLY')",
                id,title,CatalogImportService.normalize(title),origin,type,status);
        return id;
    }
    @Test void unifiedListOnlyIncludesActiveMainGamesAndStablePages() throws Exception {
        UUID a=game("Same","INTERNAL","MAIN_GAME","ACTIVE");
        UUID b=game("Same","EXTERNAL","MAIN_GAME","ACTIVE");
        game("Hidden","INTERNAL","MAIN_GAME","ARCHIVED"); game("DLC","EXTERNAL","DLC","ACTIVE");
        game("Unknown","EXTERNAL","UNKNOWN","ACTIVE");
        var first=discovery.list(new DiscoveryQuery(0,1)); var second=discovery.list(new DiscoveryQuery(1,1));
        assertEquals(2,first.totalElements()); assertNotEquals(first.items().getFirst().game().gameId(),second.items().getFirst().game().gameId());
        assertEquals(Set.of(a,b),Set.of(first.items().getFirst().game().gameId(),second.items().getFirst().game().gameId()));
        mvc.perform(get("/api/v1/game/games")).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2));
        assertTrue(discovery.list(new DiscoveryQuery(10,10)).items().isEmpty());
    }
    @Test void pageLimitsAndWritesAreRejected() throws Exception {
        for(String value:List.of("0","101","invalid")) mvc.perform(get("/api/v1/game/games").param("size",value))
                .andExpect(status().isBadRequest()).andExpect(content().contentTypeCompatibleWith("application/problem+json"));
        mvc.perform(get("/api/v1/game/games").param("page","10001")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/game/games")).andExpect(status().isForbidden());
    }
    @Test void detailUsesCanonicalIdAndPreservesUnknownOptionalMetadata() throws Exception {
        UUID id=game("Sparse external","EXTERNAL","MAIN_GAME","ACTIVE");
        mvc.perform(get("/api/v1/game/games/"+id)).andExpect(status().isOk())
                .andExpect(jsonPath("$.game.gameId").value(id.toString()))
                .andExpect(jsonPath("$.description").isEmpty()).andExpect(jsonPath("$.releaseDate").isEmpty())
                .andExpect(jsonPath("$.metadata.earlyAccess").isEmpty()).andExpect(jsonPath("$.metadata.languages").isEmpty());
        jdbc.update("update games set description='Local description',release_date='2020-01-01',early_access=true where id=?",id);
        jdbc.update("insert into game_languages(game_id,language_code) values (?,'tr')",id);
        mvc.perform(get("/api/v1/game/games/"+id)).andExpect(status().isOk())
                .andExpect(jsonPath("$.description").value("Local description"))
                .andExpect(jsonPath("$.metadata.earlyAccess").value(true)).andExpect(jsonPath("$.metadata.languages[0]").value("tr"));
    }
    @Test void searchNormalizesUnicodeAndTreatsWildcardsAsLiteral() throws Exception {
        game("Alpha world","INTERNAL","MAIN_GAME","ACTIVE"); game("Alpha other","EXTERNAL","MAIN_GAME","ACTIVE");
        game("100% fun","INTERNAL","MAIN_GAME","ACTIVE"); game("Alpha DLC","EXTERNAL","DLC","ACTIVE");
        mvc.perform(get("/api/v1/game/games/search").param("q"," ＡＬＰＨＡ "))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2));
        mvc.perform(get("/api/v1/game/games/search").param("q","100%"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get("/api/v1/game/games/search").param("q","%"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get("/api/v1/game/games/search")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/game/games/search").param("q"," ")).andExpect(status().isBadRequest());
    }
    @Test void filtersComposeWithoutDuplicatesAndUnknownMetadataDoesNotMatch() throws Exception {
        UUID id=game("Filtered","INTERNAL","MAIN_GAME","ACTIVE");
        game("Unknown metadata","INTERNAL","MAIN_GAME","ACTIVE");
        jdbc.update("update games set early_access=false,release_date='2020-01-01',editor_rank=1 where id=?",id);
        jdbc.update("insert into game_languages(game_id,language_code) values (?,'tr')",id);
        for(String kind:List.of("GENRE","PLATFORM")) {
            UUID term=UUID.randomUUID();
            jdbc.update("insert into catalog_terms(id,kind,code) values (?,?,?)",term,kind,"filter-"+kind);
            jdbc.update("insert into game_terms(game_id,term_id) values (?,?)",id,term);
        }
        mvc.perform(get("/api/v1/game/games").param("genre","filter-GENRE").param("platform","filter-PLATFORM")
                .param("language","TR").param("earlyAccess","false").param("releasedAfter","2019-01-01")
                .param("releasedBefore","2021-01-01").param("popularOnly","true"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get("/api/v1/game/games").param("earlyAccess","false"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get("/api/v1/game/games").param("language","en"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
    }
    @Test void invalidFiltersAndUnavailableRatingAreRejected() throws Exception {
        mvc.perform(get("/api/v1/game/games").param("minRating","5")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("RATING_INTEGRATION_PENDING"));
        mvc.perform(get("/api/v1/game/games").param("language","not a language")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/game/games").param("releasedAfter","2021-01-01").param("releasedBefore","2020-01-01"))
                .andExpect(status().isBadRequest());
    }
    @Test void sortsAreAllowlistedWithNullsLastAndStableTies() throws Exception {
        UUID old=game("Old","INTERNAL","MAIN_GAME","ACTIVE");
        UUID recent=game("Recent","INTERNAL","MAIN_GAME","ACTIVE");
        UUID missing=game("Missing","INTERNAL","MAIN_GAME","ACTIVE");
        jdbc.update("update games set release_date='2000-01-01',editor_rank=1 where id=?",old);
        jdbc.update("update games set release_date='2020-01-01',editor_rank=2 where id=?",recent);
        mvc.perform(get("/api/v1/game/games").param("sort","RELEASE_DATE").param("direction","DESC"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].game.gameId").value(recent.toString()))
                .andExpect(jsonPath("$.items[2].game.gameId").value(missing.toString()));
        mvc.perform(get("/api/v1/game/games").param("sort","POPULARITY").param("direction","DESC"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].game.gameId").value(old.toString()));
        for(String value:List.of("RATING","title;drop table games","unknown"))
            mvc.perform(get("/api/v1/game/games").param("sort",value)).andExpect(status().isBadRequest());
    }
    @Test void popularReadsPreparedEligibleCardsAndReportsEditorialBasis() throws Exception {
        UUID active=game("Prepared","EXTERNAL","MAIN_GAME","ACTIVE");
        UUID hidden=game("Hidden","INTERNAL","MAIN_GAME","ARCHIVED");
        UUID dlc=game("DLC","EXTERNAL","DLC","ACTIVE");
        jdbc.update("update games set editor_rank=1,popularity_updated_at=now() where id=?",hidden);
        jdbc.update("update games set editor_rank=2,popularity_updated_at=now() where id=?",dlc);
        jdbc.update("update games set editor_rank=3,popularity_updated_at=now() where id=?",active);
        mvc.perform(get("/api/v1/game/games/popular")).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1)).andExpect(jsonPath("$.popularityBasis").value("EDITORIAL"))
                .andExpect(jsonPath("$.items[0].game.gameId").value(active.toString()))
                .andExpect(jsonPath("$.items[0].popularityUpdatedAt").isNotEmpty());
        mvc.perform(post("/api/v1/game/games/popular")).andExpect(status().isForbidden());
    }
    @Test void editorialCommandReplacesOrderAtomicallyAndRequiresOperatorEvidence() {
        UUID first=game("First","INTERNAL","MAIN_GAME","ACTIVE");
        UUID second=game("Second","EXTERNAL","MAIN_GAME","ACTIVE");
        UUID actor=UUID.randomUUID();
        editorial.replace(List.of(first,second),actor,"Verified editor selection");
        editorial.replace(List.of(second,first),actor,"Verified editor selection");
        assertEquals(second,discovery.list(DiscoveryQuery.popular(0,20)).items().getFirst().game().gameId());
        assertEquals(actor,jdbc.queryForObject("select editor_actor_id from games where id=?",UUID.class,second));
        UUID hidden=game("Hidden","INTERNAL","MAIN_GAME","ARCHIVED");
        List<UUID> invalid=List.of(hidden);
        assertThrows(IllegalArgumentException.class,()->editorial.replace(invalid,actor,"Verified editor selection"));
        assertEquals(2,discovery.list(DiscoveryQuery.popular(0,20)).totalElements());
        List<UUID> duplicate=List.of(first,first);
        assertThrows(IllegalArgumentException.class,()->editorial.replace(duplicate,actor,"Verified editor selection"));
        List<UUID> valid=List.of(first);
        assertThrows(IllegalArgumentException.class,()->editorial.replace(valid,null,"Verified editor selection"));
        editorial.replace(List.of(),actor,"Clear editor selection");
        assertEquals(0,discovery.list(DiscoveryQuery.popular(0,20)).totalElements());
    }
}
