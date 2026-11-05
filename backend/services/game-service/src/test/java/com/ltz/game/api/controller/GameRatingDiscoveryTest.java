package com.ltz.game.api.controller;

import com.ltz.game.PostgresTestSupport;
import com.ltz.game.application.service.CatalogImportService;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** GAME-04/05/15: puan projeksiyonuna dayalı filtre, sıralama ve puan durumu (RATED/UNRATED/UNAVAILABLE). */
@SpringBootTest
class GameRatingDiscoveryTest extends PostgresTestSupport {
    @Autowired WebApplicationContext context;
    @Autowired JdbcTemplate jdbc;
    MockMvc mvc;

    @BeforeEach void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        reset();
    }

    @AfterEach void reset() {
        jdbc.update("delete from game_external_refs");
        jdbc.update("delete from games");
        jdbc.update("""
                update game_rating_sync_state set scan_generation = 0, lease_owner = null, lease_until = null,
                    last_attempt_at = null, last_completed_scan = null, last_completed_at = null, last_error_code = null""");
    }

    UUID game(String title, String type, String status) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into games(id,title,normalized_title,created_at,updated_at,origin,game_type,status,metadata_completeness) "
                + "values (?,?,?,now(),now(),'INTERNAL',?,?,'INDEX_ONLY')", id, title, CatalogImportService.normalize(title), type, status);
        return id;
    }

    UUID game(String title) {
        return game(title, "MAIN_GAME", "ACTIVE");
    }

    void rate(UUID game, String average, long count) {
        jdbc.update("insert into game_rating_projection(game_id,average_rating,rating_count,last_seen_scan,synced_at) "
                + "values (?,cast(? as numeric),?,1,now())", game, average, count);
    }

    void markReady() {
        jdbc.update("update game_rating_sync_state set scan_generation = 1, last_completed_scan = 1, last_completed_at = now()");
    }

    List<String> titles(String... params) throws Exception {
        var request = get("/api/v1/game/games");
        for (int index = 0; index < params.length; index += 2) request.param(params[index], params[index + 1]);
        String json = mvc.perform(request).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        return com.jayway.jsonpath.JsonPath.read(json, "$.items[*].game.title");
    }

    @Test void beforeTheFirstCompletedScanRatingsAreUnavailableNotZero() throws Exception {
        game("Alpha");
        mvc.perform(get("/api/v1/game/games")).andExpect(status().isOk())
                .andExpect(jsonPath("$.ratingsSyncedAt").isEmpty())
                .andExpect(jsonPath("$.items[0].rating.status").value("UNAVAILABLE"))
                .andExpect(jsonPath("$.items[0].rating.averageRating").isEmpty())
                .andExpect(jsonPath("$.items[0].rating.ratingCount").isEmpty());
        for (String[] params : new String[][]{{"minRating", "5"}, {"sort", "RATING"}, {"minRating", "5", "sort", "RATING", "direction", "DESC"}}) {
            var request = get("/api/v1/game/games");
            for (int index = 0; index < params.length; index += 2) request.param(params[index], params[index + 1]);
            mvc.perform(request).andExpect(status().isBadRequest())
                    .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                    .andExpect(jsonPath("$.code").value("RATING_INTEGRATION_PENDING"));
        }
        mvc.perform(get("/api/v1/game/games/search").param("q", "alpha").param("minRating", "5")).andExpect(status().isBadRequest());
    }

    @Test void afterAScanItemsReportRatedOrUnratedAndTheSyncTime() throws Exception {
        rate(game("Rated"), "7.50", 12);
        game("Fresh");
        UUID zeroed = game("Zeroed");
        jdbc.update("insert into game_rating_projection(game_id,average_rating,rating_count,last_seen_scan,synced_at) values (?,null,0,1,now())", zeroed);
        markReady();
        mvc.perform(get("/api/v1/game/games").param("sort", "TITLE")).andExpect(status().isOk())
                .andExpect(jsonPath("$.ratingsSyncedAt").isNotEmpty())
                .andExpect(jsonPath("$.items[?(@.game.title=='Rated')].rating.status").value("RATED"))
                .andExpect(jsonPath("$.items[?(@.game.title=='Rated')].rating.averageRating").value(7.5))
                .andExpect(jsonPath("$.items[?(@.game.title=='Rated')].rating.ratingCount").value(12))
                .andExpect(jsonPath("$.items[?(@.game.title=='Fresh')].rating.status").value("UNRATED"))
                .andExpect(jsonPath("$.items[?(@.game.title=='Fresh')].rating.ratingCount").value(0))
                .andExpect(jsonPath("$.items[?(@.game.title=='Zeroed')].rating.status").value("UNRATED"));
    }

    @Test void minRatingKeepsOnlyRatedGamesAtOrAboveTheThresholdAndBoundaryIsInclusive() throws Exception {
        rate(game("Low"), "4.99", 3);
        rate(game("Exact"), "5.00", 3);
        rate(game("High"), "9.10", 1);
        game("Unrated");
        markReady();
        assertEquals(List.of("Exact", "High"), titles("minRating", "5", "sort", "TITLE"));
        assertEquals(List.of("Exact", "High"), titles("minRating", "5.00", "sort", "TITLE"));
        assertEquals(List.of("High"), titles("minRating", "5.01"));
        assertEquals(List.of("Exact", "High", "Low"), titles("minRating", "1", "sort", "TITLE"));
        mvc.perform(get("/api/v1/game/games").param("minRating", "5")).andExpect(jsonPath("$.totalElements").value(2));
        assertTrue(titles("minRating", "10").isEmpty());
    }

    @Test void ratingSortPutsUnratedLastInBothDirectionsAndTiesAreStableByGameId() throws Exception {
        UUID tieA = game("Tie A"), tieB = game("Tie B");
        rate(tieA, "8.00", 2);
        rate(tieB, "8.00", 9);
        rate(game("Top"), "9.50", 1);
        rate(game("Bottom"), "2.00", 4);
        game("Unrated one");
        game("Unrated two");
        markReady();
        List<String> tied = new ArrayList<>(List.of("Tie A", "Tie B"));
        if (tieA.toString().compareTo(tieB.toString()) > 0) java.util.Collections.reverse(tied);
        List<String> descending = titles("sort", "RATING", "direction", "DESC", "size", "100");
        List<String> ascending = titles("sort", "RATING", "direction", "ASC", "size", "100");
        assertEquals(6, descending.size());
        assertEquals(List.of("Top", tied.get(0), tied.get(1), "Bottom"), descending.subList(0, 4));
        assertEquals(List.of("Bottom", tied.get(0), tied.get(1), "Top"), ascending.subList(0, 4));
        assertEquals(java.util.Set.of("Unrated one", "Unrated two"), new java.util.HashSet<>(descending.subList(4, 6)));
        assertEquals(java.util.Set.of("Unrated one", "Unrated two"), new java.util.HashSet<>(ascending.subList(4, 6)));
    }

    @Test void pagingAcrossRatingSortHasNoDuplicatesOrGapsAndEqualsTheUnpagedOrder() throws Exception {
        for (int number = 0; number < 11; number++) {
            UUID id = game("Game %02d".formatted(number));
            if (number % 4 != 0) rate(id, "%d.00".formatted(1 + number % 3), 1 + number); // çok sayıda eşitlik
        }
        markReady();
        List<String> whole = titles("sort", "RATING", "direction", "DESC", "size", "100");
        List<String> paged = new ArrayList<>();
        for (int page = 0; page < 4; page++) paged.addAll(titles("sort", "RATING", "direction", "DESC", "size", "3", "page", String.valueOf(page)));
        assertEquals(11, whole.size());
        assertEquals(whole, paged);
        assertEquals(11, new java.util.HashSet<>(paged).size());
        List<String> filteredWhole = titles("minRating", "2", "sort", "RATING", "direction", "ASC", "size", "100");
        List<String> filteredPaged = new ArrayList<>();
        for (int page = 0; page < 4; page++) filteredPaged.addAll(titles("minRating", "2", "sort", "RATING", "direction", "ASC", "size", "2", "page", String.valueOf(page)));
        assertEquals(filteredWhole, filteredPaged);
        mvc.perform(get("/api/v1/game/games").param("minRating", "2").param("size", "2"))
                .andExpect(jsonPath("$.totalElements").value(filteredWhole.size()));
    }

    @Test void ratingFilterCombinesWithOtherFiltersAndKeepsVisibilityRules() throws Exception {
        rate(game("Shared Good"), "8.00", 5);
        rate(game("Other Good"), "8.00", 5);
        rate(game("Shared Bad"), "3.00", 5);
        rate(game("Shared DLC", "DLC", "ACTIVE"), "9.00", 5);
        rate(game("Shared Archived", "MAIN_GAME", "ARCHIVED"), "9.00", 5);
        markReady();
        assertEquals(List.of("Shared Good"), titles("q", "shared", "minRating", "5"));
        mvc.perform(get("/api/v1/game/games/search").param("q", "shared").param("minRating", "5").param("sort", "RATING").param("direction", "DESC"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].game.title").value("Shared Good"));
    }

    @Test void invalidMinRatingValuesAreBadRequestsNotEmptyResults() throws Exception {
        game("Alpha");
        markReady();
        for (String value : new String[]{"0", "0.99", "10.01", "11", "-1", "5.555", "abc", "NaN", "1e2"}) {
            mvc.perform(get("/api/v1/game/games").param("minRating", value)).andExpect(status().isBadRequest())
                    .andExpect(content().contentTypeCompatibleWith("application/problem+json"));
        }
        for (String value : new String[]{"1", "10", "5.5", "5.50", "7.25"}) {
            mvc.perform(get("/api/v1/game/games").param("minRating", value)).andExpect(status().isOk());
        }
    }

    @Test void popularListAndOrdinarySortsStillWorkWithTheProjectionJoin() throws Exception {
        UUID rated = game("Beta");
        rate(rated, "6.00", 1);
        game("Alpha");
        markReady();
        assertEquals(List.of("Alpha", "Beta"), titles("sort", "TITLE"));
        assertEquals(List.of("Beta", "Alpha"), titles("sort", "TITLE", "direction", "DESC"));
        mvc.perform(get("/api/v1/game/games/popular")).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
    }
}
