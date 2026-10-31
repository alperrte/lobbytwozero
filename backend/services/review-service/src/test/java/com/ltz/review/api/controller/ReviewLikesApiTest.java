package com.ltz.review.api.controller;

import com.ltz.review.application.port.GameCatalogPort;
import com.ltz.review.application.service.AccountErasureService;
import java.util.List;
import java.util.Map;
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
import tools.jackson.databind.json.JsonMapper;
import static com.ltz.review.api.controller.RatingApiTest.actor;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class ReviewLikesApiTest {
    @Autowired WebApplicationContext context;
    @Autowired JdbcClient jdbc;
    @Autowired JsonMapper json;
    @Autowired AccountErasureService erasure;
    @MockitoBean GameCatalogPort games;
    MockMvc mvc;
    UUID author;
    UUID voter;
    UUID review;

    @BeforeEach void setup() throws Exception {
        mvc = MockMvcBuilders.webAppContextSetup(context).defaultRequest(get("/").with(csrf())).apply(springSecurity()).build();
        author = UUID.randomUUID();
        voter = UUID.randomUUID();
        review = create(author);
    }

    UUID create(UUID owner) throws Exception {
        var response = mvc.perform(post("/api/v1/review/games/" + UUID.randomUUID() + "/reviews").with(actor(owner))
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("body", "Deneyim"))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return UUID.fromString(json.readTree(response).get("id").asText());
    }

    String path() { return "/api/v1/review/reviews/" + review + "/like"; }

    @Test void likesAndUnlikesAreIdempotentAndActorSpecific() throws Exception {
        mvc.perform(put(path()).with(actor(voter))).andExpect(status().isOk())
                .andExpect(jsonPath("$.likeCount").value(1)).andExpect(jsonPath("$.likedByActor").value(true));
        mvc.perform(put(path()).with(actor(voter))).andExpect(jsonPath("$.likeCount").value(1));
        mvc.perform(get(path()).with(actor(author))).andExpect(jsonPath("$.likedByActor").value(false))
                .andExpect(jsonPath("$.likeCount").value(1));
        mvc.perform(delete(path()).with(actor(author))).andExpect(jsonPath("$.likeCount").value(1));
        mvc.perform(delete(path()).with(actor(voter))).andExpect(jsonPath("$.likeCount").value(0))
                .andExpect(jsonPath("$.likedByActor").value(false));
        mvc.perform(delete(path()).with(actor(voter))).andExpect(jsonPath("$.likeCount").value(0));
    }

    @Test void authenticationSelfVoteAndPayloadRulesAreEnforced() throws Exception {
        mvc.perform(put(path())).andExpect(status().isUnauthorized());
        mvc.perform(get(path())).andExpect(status().isUnauthorized());
        mvc.perform(delete(path())).andExpect(status().isUnauthorized());
        mvc.perform(put(path()).with(actor(author))).andExpect(status().isForbidden());
        mvc.perform(put(path()).with(actor(voter)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"userId\":\"" + author + "\"}")).andExpect(status().isBadRequest());
        mvc.perform(delete(path()).with(actor(voter)).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        mvc.perform(delete(path()).with(actor(voter)).contentType(MediaType.APPLICATION_JSON).content("x".repeat(65537)))
                .andExpect(status().isPayloadTooLarge());
        mvc.perform(put("/api/v1/review/reviews/not-uuid/like").with(actor(voter))).andExpect(status().isBadRequest());
        mvc.perform(put("/api/v1/review/reviews/" + UUID.randomUUID() + "/like").with(actor(voter)))
                .andExpect(status().isNotFound());
    }

    @Test void concurrentRepeatedAndIndependentVotesHaveExactCounts() throws Exception {
        try (var executor = Executors.newFixedThreadPool(6)) {
            Callable<Integer> same = () -> mvc.perform(put(path()).with(actor(voter))).andReturn().getResponse().getStatus();
            for (var result : executor.invokeAll(java.util.Collections.nCopies(12, same))) assertEquals(200, result.get());
            var others = java.util.stream.IntStream.range(0, 6).mapToObj(i -> (Callable<Integer>) () ->
                    mvc.perform(put(path()).with(actor(UUID.randomUUID()))).andReturn().getResponse().getStatus()).toList();
            for (var result : executor.invokeAll(others)) assertEquals(200, result.get());
        }
        mvc.perform(get(path()).with(actor(voter))).andExpect(jsonPath("$.likeCount").value(7));
    }

    @Test void deletedReviewsLoseVotesAndRejectFurtherInteractions() throws Exception {
        mvc.perform(put(path()).with(actor(voter))).andExpect(status().isOk());
        mvc.perform(delete("/api/v1/review/reviews/" + review).param("version", "0").with(actor(author)))
                .andExpect(status().isNoContent());
        assertEquals(0L, jdbc.sql("SELECT count(*) FROM review_likes WHERE review_id=?").param(review).query(Long.class).single());
        mvc.perform(put(path()).with(actor(voter))).andExpect(status().isNotFound());
        mvc.perform(delete(path()).with(actor(voter))).andExpect(status().isNotFound());
        mvc.perform(get(path()).with(actor(voter))).andExpect(status().isNotFound());
    }

    @Test void accountErasureRemovesVotesAndBlocksRecreation() throws Exception {
        mvc.perform(put(path()).with(actor(voter))).andExpect(status().isOk());
        erasure.erase(voter);
        erasure.erase(voter);
        mvc.perform(get(path()).with(actor(author))).andExpect(jsonPath("$.likeCount").value(0));
        mvc.perform(put(path()).with(actor(voter))).andExpect(status().isForbidden());
        mvc.perform(put(path()).with(actor(UUID.randomUUID()))).andExpect(status().isOk());
        erasure.erase(author);
        assertEquals(0L, jdbc.sql("SELECT count(*) FROM review_likes WHERE review_id=?").param(review).query(Long.class).single());
    }

    @Test void concurrentErasureOfMutuallyVotingAuthorsHasNoDeadlock() throws Exception {
        UUID second = create(voter);
        mvc.perform(put(path()).with(actor(voter))).andExpect(status().isOk());
        mvc.perform(put("/api/v1/review/reviews/" + second + "/like").with(actor(author))).andExpect(status().isOk());
        try (var executor = Executors.newFixedThreadPool(2)) {
            Callable<Integer> first = () -> { erasure.erase(author); return 204; };
            Callable<Integer> other = () -> { erasure.erase(voter); return 204; };
            for (var result : executor.invokeAll(List.of(first, other))) assertEquals(204, result.get());
        }
        assertEquals(0L, jdbc.sql("SELECT count(*) FROM review_likes WHERE review_id IN (?,?)").params(review, second).query(Long.class).single());
    }

    @Test void deletionRaceNeverLeavesAnOrphanVote() throws Exception {
        try (var executor = Executors.newFixedThreadPool(2)) {
            Callable<Integer> vote = () -> mvc.perform(put(path()).with(actor(voter))).andReturn().getResponse().getStatus();
            Callable<Integer> remove = () -> mvc.perform(delete("/api/v1/review/reviews/" + review).param("version", "0")
                    .with(actor(author))).andReturn().getResponse().getStatus();
            var results = executor.invokeAll(List.of(vote, remove));
            assertTrue(List.of(200, 404).contains(results.get(0).get()));
            assertEquals(204, results.get(1).get());
        }
        assertEquals(0L, jdbc.sql("SELECT count(*) FROM review_likes WHERE review_id=?").param(review).query(Long.class).single());
    }
}
