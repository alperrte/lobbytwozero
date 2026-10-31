package com.ltz.review.api.controller;

import com.ltz.review.application.port.GameCatalogPort;
import com.ltz.review.domain.exception.ReviewException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.doThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class RatingApiTest {
    @Autowired WebApplicationContext context;
    @Autowired JdbcClient jdbc;
    @Autowired com.ltz.review.application.service.AccountErasureService erasure;
    @MockitoBean GameCatalogPort games;
    MockMvc mvc;
    UUID user;
    UUID game;

    @BeforeEach void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(context).defaultRequest(get("/")
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf()))
                .apply(springSecurity()).build();
        user = UUID.randomUUID();
        game = UUID.randomUUID();
    }

    String path() { return "/api/v1/review/games/" + game + "/rating"; }

    @Test void ratingWithoutTextCanBeCreatedReadAndUpdated() throws Exception {
        rate(1).andExpect(status().isOk()).andExpect(jsonPath("$.rating").value(1)).andExpect(jsonPath("$.version").value(0));
        mvc.perform(get(path()).with(actor(user))).andExpect(status().isOk()).andExpect(jsonPath("$.gameId").value(game.toString()));
        rate(10).andExpect(status().isOk()).andExpect(jsonPath("$.rating").value(10)).andExpect(jsonPath("$.version").value(1));
        assertEquals(1L, count());
    }

    @Test void identicalRetryPreservesVersionAndTimestamps() throws Exception {
        String first = rate(7).andReturn().getResponse().getContentAsString();
        String repeat = rate(7).andReturn().getResponse().getContentAsString();
        assertEquals(first, repeat);
        assertEquals(1L, count());
    }

    @Test void rejectsInvalidAndMissingRatingsAndMassAssignment() throws Exception {
        for (String body : List.of("{\"rating\":0}", "{\"rating\":11}", "{\"rating\":null}", "{}",
                "{\"rating\":1.5}", "{\"rating\":\"7\"}", "{\"rating\":7,\"userId\":\"" + UUID.randomUUID() + "\"}")) {
            mvc.perform(put(path()).with(actor(user)).contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400));
        }
        assertEquals(0L, count());
    }

    @Test void anonymousAndCookieAndSpoofedIdentityCannotWrite() throws Exception {
        mvc.perform(put(path()).contentType(MediaType.APPLICATION_JSON).content("{\"rating\":7}")
                .header("X-User-Id", user).header("X-Role", "ADMIN"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get(path())).andExpect(status().isUnauthorized());
        assertEquals(0L, count());
    }

    @Test void missingRoleIsForbiddenAndAnotherUserCannotReadOrOverwriteMine() throws Exception {
        mvc.perform(put(path()).with(jwt().jwt(token -> token.subject(user.toString())))
                .contentType(MediaType.APPLICATION_JSON).content("{\"rating\":7}"))
                .andExpect(status().isForbidden());
        rate(7).andExpect(status().isOk());
        UUID other = UUID.randomUUID();
        mvc.perform(get(path()).with(actor(other))).andExpect(status().isNotFound());
        mvc.perform(put(path()).with(actor(other)).contentType(MediaType.APPLICATION_JSON).content("{\"rating\":2}"))
                .andExpect(status().isOk());
        mvc.perform(get(path()).with(actor(user))).andExpect(jsonPath("$.rating").value(7));
    }

    @Test void unavailableGameOrDependencyFailureNeverWrites() throws Exception {
        doThrow(new ReviewException(404, "Oyun bulunamadı.")).when(games).requireAvailable(game);
        rate(7).andExpect(status().isNotFound());
        doThrow(new ReviewException(503, "Geçici hata.")).when(games).requireAvailable(game);
        rate(7).andExpect(status().isServiceUnavailable());
        assertEquals(0L, count());
    }

    @Test void concurrentRepeatedRatingsProduceOnlyOneRow() throws Exception {
        try (var executor = Executors.newFixedThreadPool(8)) {
            Callable<Integer> task = () -> rate(7).andReturn().getResponse().getStatus();
            for (var future : executor.invokeAll(java.util.Collections.nCopies(16, task))) assertEquals(200, future.get());
        }
        assertEquals(1L, count());
        mvc.perform(get(path()).with(actor(user))).andExpect(jsonPath("$.version").value(0));
    }

    @Test void databaseRejectsDuplicateAndOutOfRangeRatings() throws Exception {
        rate(7).andExpect(status().isOk());
        org.junit.jupiter.api.Assertions.assertThrows(org.springframework.dao.DataIntegrityViolationException.class,
                () -> jdbc.sql("INSERT INTO game_ratings (id,user_id,game_id,rating,created_at,updated_at) VALUES (?,?,?,7,now(),now())")
                        .params(UUID.randomUUID(), user, game).update());
        org.junit.jupiter.api.Assertions.assertThrows(org.springframework.dao.DataIntegrityViolationException.class,
                () -> jdbc.sql("UPDATE game_ratings SET rating=11 WHERE user_id=?").param(user).update());
    }

    @Test void accountErasureDeletesOnlyTargetRatingsAndIsIdempotent() throws Exception {
        rate(7).andExpect(status().isOk());
        UUID other = UUID.randomUUID();
        mvc.perform(put(path()).with(actor(other)).contentType(MediaType.APPLICATION_JSON).content("{\"rating\":2}"))
                .andExpect(status().isOk());
        erasure.erase(user);
        erasure.erase(user);
        assertEquals(0L, count());
        mvc.perform(get(path()).with(actor(other))).andExpect(status().isOk()).andExpect(jsonPath("$.rating").value(2));
    }

    long count() {
        return jdbc.sql("SELECT count(*) FROM game_ratings WHERE user_id=? AND game_id=?").params(user, game).query(Long.class).single();
    }

    org.springframework.test.web.servlet.ResultActions rate(int rating) throws Exception {
        return mvc.perform(put(path()).with(actor(user)).contentType(MediaType.APPLICATION_JSON).content("{\"rating\":" + rating + "}"));
    }

    static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor actor(UUID id) {
        return jwt().jwt(token -> token.subject(id.toString()).claim("roles", List.of("PLAYER")))
                .authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_PLAYER"));
    }
}
