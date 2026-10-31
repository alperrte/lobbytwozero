package com.ltz.review.api.controller;

import com.ltz.review.application.port.GameCatalogPort;
import com.ltz.review.infrastructure.persistence.repository.FeaturedReviewRepository;
import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import static com.ltz.review.api.controller.RatingApiTest.actor;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class FeaturedReviewsApiTest {
    @Autowired WebApplicationContext context;
    @Autowired JdbcClient jdbc;
    @MockitoBean GameCatalogPort games;
    @MockitoSpyBean FeaturedReviewRepository repository;
    MockMvc mvc;
    UUID game;
    UUID reader;

    @BeforeEach void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(context).defaultRequest(get("/").with(csrf())).apply(springSecurity()).build();
        game = UUID.randomUUID();
        reader = UUID.randomUUID();
    }

    String path() { return "/api/v1/review/games/" + game + "/reviews/featured"; }

    UUID seed(UUID id, UUID owner, String date, boolean visible) {
        var time = OffsetDateTime.parse(date);
        jdbc.sql("""
                INSERT INTO reviews (id,user_id,game_id,body,status,created_at,updated_at,deleted_at)
                VALUES (?,?,?,?,?,?,?,?)
                """).params(id, owner, game, visible ? "Deneyim" : null, visible ? "VISIBLE" : "DELETED",
                        time, time, visible ? null : time).update();
        return id;
    }

    UUID seed(String date) { return seed(UUID.randomUUID(), UUID.randomUUID(), date, true); }

    void vote(UUID review) {
        jdbc.sql("INSERT INTO review_likes (review_id,user_id,created_at) VALUES (?,?,clock_timestamp())")
                .params(review, UUID.randomUUID()).update();
    }

    @Test void ranksByLikesThenCreationTimeThenUuidWithoutExposingAuthor() throws Exception {
        UUID older = seed("2026-01-01T00:00:00Z");
        UUID newer = seed("2026-02-01T00:00:00Z");
        long prefix = UUID.randomUUID().getMostSignificantBits();
        UUID low = seed(new UUID(prefix, 0), reader, "2026-03-01T00:00:00Z", true);
        UUID high = seed(new UUID(prefix, 1), UUID.randomUUID(), "2026-03-01T00:00:00Z", true);
        vote(older);
        vote(older);
        vote(newer);
        vote(low);
        vote(high);
        for (int i = 0; i < 2; i++) {
            mvc.perform(get(path()).with(actor(reader))).andExpect(status().isOk())
                    .andExpect(jsonPath("$.items[0].review.id").value(older.toString()))
                    .andExpect(jsonPath("$.items[0].likeCount").value(2))
                    .andExpect(jsonPath("$.items[1].review.id").value(low.toString()))
                    .andExpect(jsonPath("$.items[1].review.ownedByActor").value(true))
                    .andExpect(jsonPath("$.items[2].review.id").value(high.toString()))
                    .andExpect(jsonPath("$.items[3].review.id").value(newer.toString()))
                    .andExpect(jsonPath("$.items[0].review.userId").doesNotExist())
                    .andExpect(jsonPath("$.totalElements").value(4));
        }
        verifyNoInteractions(games);
    }

    @Test void filtersGameVisibilityAndMinimumLikesIncludingZeroVoteReviews() throws Exception {
        UUID visible = seed("2026-01-01T00:00:00Z");
        seed("2026-02-01T00:00:00Z");
        UUID deleted = seed(UUID.randomUUID(), UUID.randomUUID(), "2026-03-01T00:00:00Z", false);
        vote(visible);
        vote(deleted);
        vote(deleted);
        UUID originalGame = game;
        game = UUID.randomUUID();
        vote(seed("2026-04-01T00:00:00Z"));
        game = originalGame;
        mvc.perform(get(path()).with(actor(reader))).andExpect(jsonPath("$.totalElements").value(2));
        mvc.perform(get(path()).param("minLikes", "1").with(actor(reader))).andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1)).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].review.id").value(visible.toString()));
        mvc.perform(get(path()).param("minLikes", "2").with(actor(reader))).andExpect(jsonPath("$.items").isEmpty())
                .andExpect(jsonPath("$.totalPages").value(0));
    }

    @Test void paginatesWithStableTiesAndAccurateTotals() throws Exception {
        UUID first = seed("2026-01-01T00:00:00Z");
        UUID second = seed("2026-01-01T00:00:00Z");
        String low = first.toString().compareTo(second.toString()) < 0 ? first.toString() : second.toString();
        String high = low.equals(first.toString()) ? second.toString() : first.toString();
        mvc.perform(get(path()).param("size", "1").with(actor(reader)))
                .andExpect(jsonPath("$.items[0].review.id").value(low)).andExpect(jsonPath("$.totalPages").value(2));
        mvc.perform(get(path()).param("size", "1").param("page", "1").with(actor(reader)))
                .andExpect(jsonPath("$.items[0].review.id").value(high)).andExpect(jsonPath("$.page").value(1));
        mvc.perform(get(path()).param("size", "1").param("page", "2").with(actor(reader)))
                .andExpect(jsonPath("$.items").isEmpty()).andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test void emptyGamesAndMaximumBoundsAreSafeWithoutExternalCalls() throws Exception {
        mvc.perform(get(path()).with(actor(reader))).andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty()).andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20)).andExpect(jsonPath("$.totalPages").value(0));
        mvc.perform(get(path()).param("page", "10000").param("size", "50").param("minLikes", "2147483647")
                .with(actor(reader))).andExpect(status().isOk()).andExpect(jsonPath("$.items").isEmpty());
        verifyNoInteractions(games);
    }

    @Test void invalidQueriesAndIdentifiersProduceProblemDetails() throws Exception {
        for (String[] invalid : new String[][] {{"page", "-1"}, {"page", "10001"}, {"size", "0"}, {"size", "51"},
                {"minLikes", "-1"}, {"minLikes", "2147483648"}, {"page", "abc"}, {"size", "2147483648"}}) {
            mvc.perform(get(path()).param(invalid[0], invalid[1]).with(actor(reader))).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400));
        }
        mvc.perform(get("/api/v1/review/games/invalid/reviews/featured").with(actor(reader)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400));
        mvc.perform(get(path())).andExpect(status().isUnauthorized());
    }

    @Test void committedVoteAndRemovalChangesRankingAndDeletedReviewDisappears() throws Exception {
        UUID owner = UUID.randomUUID();
        UUID older = seed(UUID.randomUUID(), owner, "2026-01-01T00:00:00Z", true);
        UUID newer = seed("2026-02-01T00:00:00Z");
        String like = "/api/v1/review/reviews/" + older + "/like";
        mvc.perform(put(like).with(actor(reader))).andExpect(status().isOk());
        mvc.perform(get(path()).with(actor(reader))).andExpect(jsonPath("$.items[0].review.id").value(older.toString()));
        mvc.perform(delete(like).with(actor(reader))).andExpect(status().isOk());
        mvc.perform(get(path()).with(actor(reader))).andExpect(jsonPath("$.items[0].review.id").value(newer.toString()));
        mvc.perform(delete("/api/v1/review/reviews/" + older).param("version", "0").with(actor(owner)))
                .andExpect(status().isNoContent());
        mvc.perform(get(path()).with(actor(reader))).andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test void countAndItemsUseOneSnapshotDuringConcurrentInsertion() throws Exception {
        doAnswer(invocation -> {
            long total = (long) invocation.callRealMethod();
            CompletableFuture.runAsync(() -> seed("2026-01-01T00:00:00Z")).join();
            return total;
        }).when(repository).count(game, 0);
        mvc.perform(get(path()).with(actor(reader))).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0)).andExpect(jsonPath("$.items").isEmpty());
        assertEquals(1L, jdbc.sql("SELECT count(*) FROM reviews WHERE game_id=?").param(game).query(Long.class).single());
    }
}
