package com.ltz.review.api.controller;

import com.ltz.review.application.port.GameCatalogPort;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import static com.ltz.review.api.controller.RatingApiTest.actor;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Kullanıcının kendi incelemelerini okuma: oyun başına tek inceleme ve tüm oyunlarda sayfalı liste. */
@SpringBootTest
class MyReviewsApiTest {
    @Autowired WebApplicationContext context;
    @Autowired JdbcClient jdbc;
    @MockitoBean GameCatalogPort games;
    MockMvc mvc;
    UUID me;
    UUID game;

    @BeforeEach void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(context).defaultRequest(get("/").with(csrf())).apply(springSecurity()).build();
        me = UUID.randomUUID();
        game = UUID.randomUUID();
    }

    String gamePath(UUID target) { return "/api/v1/review/games/" + target + "/reviews/mine"; }

    UUID seed(UUID owner, UUID target, String date, boolean visible) {
        UUID id = UUID.randomUUID();
        var time = OffsetDateTime.parse(date);
        jdbc.sql("""
                INSERT INTO reviews (id,user_id,game_id,body,status,created_at,updated_at,deleted_at)
                VALUES (?,?,?,?,?,?,?,?)
                """).params(id, owner, target, visible ? "Deneyim" : null, visible ? "VISIBLE" : "DELETED",
                        time, time, visible ? null : time).update();
        return id;
    }

    @Test void returnsTheCallersVisibleReviewForTheGameWithItsVersion() throws Exception {
        UUID mine = seed(me, game, "2026-01-01T00:00:00Z", true);
        seed(UUID.randomUUID(), game, "2026-01-02T00:00:00Z", true);
        mvc.perform(get(gamePath(game)).with(actor(me))).andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(mine.toString()))
                .andExpect(jsonPath("$.gameId").value(game.toString()))
                .andExpect(jsonPath("$.ownedByActor").value(true))
                .andExpect(jsonPath("$.version").value(0))
                .andExpect(jsonPath("$.body").value("Deneyim"))
                .andExpect(jsonPath("$.userId").doesNotExist());
        verifyNoInteractions(games);
    }

    @Test void answersNotFoundWhenTheCallerHasNoVisibleReviewForTheGame() throws Exception {
        seed(UUID.randomUUID(), game, "2026-01-02T00:00:00Z", true);
        mvc.perform(get(gamePath(game)).with(actor(me))).andExpect(status().isNotFound());
        seed(me, game, "2026-01-01T00:00:00Z", false);
        mvc.perform(get(gamePath(game)).with(actor(me))).andExpect(status().isNotFound());
        mvc.perform(get(gamePath(UUID.randomUUID())).with(actor(me))).andExpect(status().isNotFound());
    }

    @Test void neverReturnsAnotherUsersReview() throws Exception {
        UUID other = UUID.randomUUID();
        UUID theirs = seed(other, game, "2026-01-01T00:00:00Z", true);
        mvc.perform(get(gamePath(game)).with(actor(me))).andExpect(status().isNotFound());
        mvc.perform(get(gamePath(game)).with(actor(other))).andExpect(jsonPath("$.id").value(theirs.toString()));
    }

    @Test void followsTheReviewLifecycleAfterDeleteAndRecreate() throws Exception {
        org.mockito.Mockito.doNothing().when(games).requireAvailable(game);
        String created = mvc.perform(post("/api/v1/review/games/" + game + "/reviews").with(actor(me))
                .contentType(MediaType.APPLICATION_JSON).content("{\"body\":\"İlk\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String firstId = com.jayway.jsonpath.JsonPath.read(created, "$.id");
        mvc.perform(get(gamePath(game)).with(actor(me))).andExpect(jsonPath("$.id").value(firstId));
        mvc.perform(delete("/api/v1/review/reviews/" + firstId).param("version", "0").with(actor(me))).andExpect(status().isNoContent());
        mvc.perform(get(gamePath(game)).with(actor(me))).andExpect(status().isNotFound());
        String second = mvc.perform(post("/api/v1/review/games/" + game + "/reviews").with(actor(me))
                .contentType(MediaType.APPLICATION_JSON).content("{\"body\":\"İkinci\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        mvc.perform(get(gamePath(game)).with(actor(me)))
                .andExpect(jsonPath("$.id").value((String) com.jayway.jsonpath.JsonPath.read(second, "$.id")))
                .andExpect(jsonPath("$.body").value("İkinci"));
    }

    @Test void rejectsAnonymousCallersAndInvalidIdentifiers() throws Exception {
        mvc.perform(get(gamePath(game))).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/review/reviews/mine")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/review/games/not-a-uuid/reviews/mine").with(actor(me)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400));
    }

    @Test void listsOnlyTheCallersVisibleReviewsNewestFirstAcrossGames() throws Exception {
        UUID oldest = seed(me, UUID.randomUUID(), "2026-01-01T00:00:00Z", true);
        UUID newest = seed(me, UUID.randomUUID(), "2026-03-01T00:00:00Z", true);
        UUID middle = seed(me, UUID.randomUUID(), "2026-02-01T00:00:00Z", true);
        seed(me, UUID.randomUUID(), "2026-04-01T00:00:00Z", false);
        seed(UUID.randomUUID(), UUID.randomUUID(), "2026-05-01T00:00:00Z", true);
        mvc.perform(get("/api/v1/review/reviews/mine").with(actor(me))).andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(3))
                .andExpect(jsonPath("$.items[0].id").value(newest.toString()))
                .andExpect(jsonPath("$.items[1].id").value(middle.toString()))
                .andExpect(jsonPath("$.items[2].id").value(oldest.toString()))
                .andExpect(jsonPath("$.items[0].ownedByActor").value(true))
                .andExpect(jsonPath("$.items[0].userId").doesNotExist())
                .andExpect(jsonPath("$.totalElements").value(3)).andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.page").value(0)).andExpect(jsonPath("$.size").value(20));
        verifyNoInteractions(games);
    }

    @Test void paginatesWithAccurateTotalsAndSafeEmptyResults() throws Exception {
        for (int i = 1; i <= 5; i++) seed(me, UUID.randomUUID(), "2026-01-0" + i + "T00:00:00Z", true);
        mvc.perform(get("/api/v1/review/reviews/mine").param("size", "2").with(actor(me)))
                .andExpect(jsonPath("$.items.length()").value(2)).andExpect(jsonPath("$.totalElements").value(5))
                .andExpect(jsonPath("$.totalPages").value(3));
        mvc.perform(get("/api/v1/review/reviews/mine").param("size", "2").param("page", "2").with(actor(me)))
                .andExpect(jsonPath("$.items.length()").value(1));
        mvc.perform(get("/api/v1/review/reviews/mine").param("size", "2").param("page", "9").with(actor(me)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items").isEmpty()).andExpect(jsonPath("$.totalElements").value(5));
        mvc.perform(get("/api/v1/review/reviews/mine").with(actor(UUID.randomUUID()))).andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty()).andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.totalPages").value(0));
    }

    @Test void rejectsOutOfRangePaging() throws Exception {
        for (String[] invalid : new String[][] {{"page", "-1"}, {"page", "10001"}, {"size", "0"}, {"size", "51"}, {"page", "abc"}}) {
            mvc.perform(get("/api/v1/review/reviews/mine").param(invalid[0], invalid[1]).with(actor(me)))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400));
        }
    }

    @Test void theFixedMinePathIsNotTreatedAsAReviewIdentifier() throws Exception {
        UUID mine = seed(me, game, "2026-01-01T00:00:00Z", true);
        mvc.perform(get("/api/v1/review/reviews/mine").with(actor(me))).andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(mine.toString()));
        mvc.perform(get("/api/v1/review/reviews/" + mine).with(actor(me))).andExpect(jsonPath("$.id").value(mine.toString()));
    }
}
