package com.ltz.review.api.controller;

import com.ltz.review.application.port.GameCatalogPort;
import com.ltz.review.application.service.AccountErasureService;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import static com.ltz.review.api.controller.RatingApiTest.actor;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class RatingSummaryApiTest {
    @Autowired WebApplicationContext context;
    @Autowired JdbcClient jdbc;
    @Autowired AccountErasureService erasure;
    @MockitoBean GameCatalogPort games;
    MockMvc mvc;
    UUID game;
    UUID user;

    @BeforeEach void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(context).defaultRequest(get("/").with(csrf())).apply(springSecurity()).build();
        game = UUID.randomUUID();
        user = UUID.randomUUID();
    }

    String path() { return "/api/v1/review/games/" + game + "/rating-summary"; }

    void seed(UUID owner, int rating) {
        jdbc.sql("INSERT INTO game_ratings (id,user_id,game_id,rating,created_at,updated_at) VALUES (?,?,?,?,now(),now())")
                .params(UUID.randomUUID(), owner, game, rating).update();
    }

    @Test void emptyGameHasNullAverageAndZeroCountWithoutCatalogCall() throws Exception {
        mvc.perform(get(path()).with(actor(user))).andExpect(status().isOk())
                .andExpect(jsonPath("$.gameId").value(game.toString())).andExpect(jsonPath("$.averageRating").isEmpty())
                .andExpect(jsonPath("$.ratingCount").value(0)).andExpect(jsonPath("$.userId").doesNotExist());
        verifyNoInteractions(games);
    }

    @Test void usesOnlyRatingsAndRoundsPositiveHalfUpToTwoDecimals() throws Exception {
        for (int i = 0; i < 7; i++) seed(UUID.randomUUID(), 1);
        seed(user, 2);
        jdbc.sql("INSERT INTO game_recommendations (id,user_id,game_id,recommended,created_at,updated_at) VALUES (?,?,?,true,now(),now())")
                .params(UUID.randomUUID(), UUID.randomUUID(), game).update();
        jdbc.sql("INSERT INTO reviews (id,user_id,game_id,body,status,created_at,updated_at,deleted_at) VALUES (?,?,?,NULL,'DELETED',now(),now(),now())")
                .params(UUID.randomUUID(), UUID.randomUUID(), game).update();
        mvc.perform(get(path()).with(actor(user))).andExpect(status().isOk())
                .andExpect(jsonPath("$.averageRating").value(1.13)).andExpect(jsonPath("$.ratingCount").value(8));
        verifyNoInteractions(games);
    }

    @Test void upsertAndRepeatedRatingNeverInflateCount() throws Exception {
        seed(UUID.randomUUID(), 1);
        String ratePath = "/api/v1/review/games/" + game + "/rating";
        for (int value : new int[] {10, 10, 4}) {
            mvc.perform(put(ratePath).with(actor(user)).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"rating\":" + value + "}")).andExpect(status().isOk());
        }
        mvc.perform(get(path()).with(actor(user))).andExpect(jsonPath("$.averageRating").value(2.5))
                .andExpect(jsonPath("$.ratingCount").value(2));
    }

    @Test void erasureRemovesRatingWhileUnrelatedGameAndUserRemain() throws Exception {
        seed(user, 10);
        seed(UUID.randomUUID(), 2);
        UUID original = game;
        game = UUID.randomUUID();
        seed(UUID.randomUUID(), 7);
        game = original;
        erasure.erase(user);
        mvc.perform(get(path()).with(actor(UUID.randomUUID()))).andExpect(jsonPath("$.averageRating").value(2))
                .andExpect(jsonPath("$.ratingCount").value(1));
    }

    @Test void concurrentSameUserUpdatesStillCountOnce() throws Exception {
        try (var executor = Executors.newFixedThreadPool(4)) {
            Callable<Integer> update = () -> mvc.perform(put("/api/v1/review/games/" + game + "/rating").with(actor(user))
                    .contentType(MediaType.APPLICATION_JSON).content("{\"rating\":8}")).andReturn().getResponse().getStatus();
            for (var result : executor.invokeAll(Collections.nCopies(8, update))) assertEquals(200, result.get());
        }
        mvc.perform(get(path()).with(actor(user))).andExpect(jsonPath("$.averageRating").value(8))
                .andExpect(jsonPath("$.ratingCount").value(1));
    }

    @Test void enforcesAuthenticationRolesAndUuidValidation() throws Exception {
        mvc.perform(get(path())).andExpect(status().isUnauthorized());
        mvc.perform(get(path()).with(jwt().authorities(new SimpleGrantedAuthority("ROLE_GUEST")))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/review/games/invalid/rating-summary").with(actor(user)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400));
    }
}
