package com.ltz.hardware.api.controller;

import com.ltz.hardware.application.port.HardwareReviewAudiencePolicy;
import com.ltz.hardware.domain.entity.HardwareReviewText;
import com.ltz.hardware.infrastructure.persistence.PostgresIntegrationSupport;
import com.ltz.hardware.infrastructure.persistence.repository.HardwareReviewWriteRepository;
import com.ltz.hardware.infrastructure.security.UnavailableHardwareReviewAudiencePolicy;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;
import static com.ltz.hardware.api.controller.HardwareProfileControllerTest.actor;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"logging.level.root=INFO", "debug=false"})
@AutoConfigureMockMvc
class HardwareReviewControllerTest extends PostgresIntegrationSupport {
    private static final String BASE = "/api/v1/hardware";
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired HardwareReviewWriteRepository writes;
    @MockitoBean HardwareReviewAudiencePolicy audience;
    UUID owner;
    UUID viewer;
    UUID product;
    UUID second;

    @BeforeEach
    void prepare() {
        owner = UUID.randomUUID();
        viewer = UUID.randomUUID();
        jdbc.update("DELETE FROM hardware_campaigns");
        jdbc.update("DELETE FROM hardware_profiles");
        jdbc.update("DELETE FROM hardware_product_brands");
        jdbc.update("DELETE FROM hardware_products");
        product = product();
        second = product();
        when(audience.evaluate(any())).thenReturn(new HardwareReviewAudiencePolicy.Decision(true, Set.of()));
    }

    private UUID product() {
        var id = UUID.randomUUID();
        jdbc.update("INSERT INTO hardware_products(id,category,model,normalized_model) VALUES (?,'CPU',?,?)",
                id, "Model " + id, "model " + id);
        return id;
    }
    private String collection(UUID id) { return BASE + "/catalog/products/" + id + "/reviews"; }
    private String item(UUID id) { return BASE + "/reviews/" + id; }
    private String content(String body, Integer rating) {
        var result = new HashMap<String, Object>();
        result.put("body", body);
        if (rating != null) result.put("rating", rating);
        return json.writeValueAsString(result);
    }
    private UUID create(UUID author, UUID id, String body, Integer rating) throws Exception {
        var result = mvc.perform(post(collection(id)).with(actor(author)).with(csrf()).contentType("application/json")
                .content(content(body, rating))).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return UUID.fromString(json.readTree(result).get("id").asText());
    }

    @Test
    void createsPlainReviewWithoutCatalogWritePermissionOrPrivateAuthorFields() throws Exception {
        var result = mvc.perform(post(collection(product)).with(actor(owner)).with(csrf()).contentType("application/json")
                .content(content("  Useful\r\nreview\ttext  ", 5)))
                .andExpect(status().isCreated()).andExpect(header().exists("Location"))
                .andExpect(header().string("Cache-Control", "no-store, private"))
                .andExpect(jsonPath("$.body").value("Useful\nreview\ttext"))
                .andExpect(jsonPath("$.rating").value(5)).andExpect(jsonPath("$.version").value(0))
                .andExpect(jsonPath("$.status").value("VISIBLE")).andExpect(jsonPath("$.ownedByActor").value(true))
                .andExpect(jsonPath("$.userId").doesNotExist()).andExpect(jsonPath("$.bodyHash").doesNotExist())
                .andExpect(jsonPath("$.reportCount").doesNotExist()).andReturn().getResponse().getContentAsString();
        assertThat(result).doesNotContain(owner.toString(), "peer:", "authorId");
        var id = UUID.fromString(json.readTree(result).get("id").asText());
        mvc.perform(get(item(id)).with(actor(viewer))).andExpect(status().isOk())
                .andExpect(jsonPath("$.ownedByActor").value(false));
    }

    @Test
    void supportsOptionalRatingAndNoFabricatedAverage() throws Exception {
        create(owner, product, "Text without a score", null);
        mvc.perform(get(collection(product)).with(actor(viewer))).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.summary.ratedReviews").value(0))
                .andExpect(jsonPath("$.summary.averageRating").isEmpty());
        mvc.perform(get(collection(second)).with(actor(viewer))).andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty()).andExpect(jsonPath("$.totalPages").value(0));
    }

    @Test
    void enforcesOwnerVersionSoftDeletionAndAllowsReplacementAfterDeletion() throws Exception {
        var id = create(owner, product, "Initial text", 4);
        var update = "{\"reviewVersion\":0,\"body\":\"Updated text\",\"rating\":3}";
        mvc.perform(put(item(id)).with(actor(viewer)).with(csrf()).contentType("application/json").content(update))
                .andExpect(status().isNotFound());
        mvc.perform(delete(item(id)).with(actor(viewer)).with(csrf()).param("reviewVersion", "0"))
                .andExpect(status().isNotFound());
        mvc.perform(put(item(id)).with(actor(owner)).with(csrf()).contentType("application/json").content(update))
                .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1));
        mvc.perform(put(item(id)).with(actor(owner)).with(csrf()).contentType("application/json").content(update))
                .andExpect(status().isConflict());
        mvc.perform(delete(item(id)).with(actor(owner)).with(csrf()).param("reviewVersion", "0"))
                .andExpect(status().isConflict());
        mvc.perform(delete(item(id)).with(actor(owner)).with(csrf()).param("reviewVersion", "1"))
                .andExpect(status().isNoContent());
        var row = jdbc.queryForMap("SELECT status,body,rating,body_hash,deleted_at,version FROM hardware_reviews WHERE id=?", id);
        assertThat(row).containsEntry("status", "DELETED");
        assertThat(row.get("body")).isNull();
        assertThat(row.get("rating")).isNull();
        assertThat(row.get("body_hash")).isNull();
        assertThat(row.get("deleted_at")).isNotNull();
        assertThat(row).containsEntry("version", 2L);
        mvc.perform(get(item(id)).with(actor(owner))).andExpect(status().isNotFound());
        mvc.perform(get(BASE + "/reviews/mine").with(actor(owner))).andExpect(jsonPath("$.totalElements").value(0));
        create(owner, product, "Updated text", 3);
    }

    @Test
    void databaseUniquenessPreventsDuplicateProductAndNormalizedCopySpam() throws Exception {
        var id = create(owner, product, "GOOD\n review", 4);
        mvc.perform(post(collection(product)).with(actor(owner)).with(csrf()).contentType("application/json")
                .content(content("Different content", 5))).andExpect(status().isConflict());
        mvc.perform(post(collection(second)).with(actor(owner)).with(csrf()).contentType("application/json")
                .content(content(" good   REVIEW ", 5))).andExpect(status().isConflict());
        jdbc.update("UPDATE hardware_reviews SET status='HIDDEN' WHERE id=?", id);
        mvc.perform(post(collection(product)).with(actor(owner)).with(csrf()).contentType("application/json")
                .content(content("Try to bypass moderation", 5))).andExpect(status().isConflict());
        create(viewer, product, "good review", 3);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM hardware_reviews", Long.class)).isEqualTo(2);
    }

    @Test
    void hiddenReviewCannotBeEditedOrExposedToAnotherUserButOwnerCanDeleteIt() throws Exception {
        var id = create(owner, product, "Hidden content", 1);
        jdbc.update("UPDATE hardware_reviews SET status='HIDDEN' WHERE id=?", id);
        mvc.perform(get(item(id)).with(actor(owner))).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("HIDDEN"));
        mvc.perform(get(BASE + "/reviews/mine").with(actor(owner))).andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get(BASE + "/reviews/mine").with(actor(viewer))).andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get(item(id)).with(actor(viewer))).andExpect(status().isNotFound());
        mvc.perform(put(item(id)).with(actor(owner)).with(csrf()).contentType("application/json")
                .content("{\"reviewVersion\":0,\"body\":\"Bypass hidden\"}"))
                .andExpect(status().isConflict());
        mvc.perform(delete(item(id)).with(actor(owner)).with(csrf()).param("reviewVersion", "0"))
                .andExpect(status().isNoContent());
    }

    @Test
    void filtersAudienceBlocksHiddenAndDeletedBeforePaginationTotalsAndAverage() throws Exception {
        var a = create(owner, product, "Five star text", 5);
        var other = UUID.randomUUID();
        var b = create(other, product, "Three star text", 3);
        var blocked = UUID.randomUUID();
        create(blocked, product, "Blocked text", 1);
        var unknown = create(UUID.randomUUID(), product, "Unrated text", null);
        var hidden = create(UUID.randomUUID(), product, "Hidden rating", 2);
        var deletedAuthor = UUID.randomUUID();
        var deleted = create(deletedAuthor, product, "Deleted rating", 1);
        jdbc.update("UPDATE hardware_reviews SET status='HIDDEN' WHERE id=?", hidden);
        mvc.perform(delete(item(deleted)).with(actor(deletedAuthor)).with(csrf()).param("reviewVersion", "0"))
                .andExpect(status().isNoContent());
        jdbc.update("UPDATE hardware_reviews SET created_at='2026-01-01T00:00:00Z'");
        var visible = List.of(a, b, unknown).stream().sorted().toList();
        // PostgreSQL UUID ordering compares unsigned bytes; obtain its canonical expected order.
        var order = jdbc.queryForList("SELECT id FROM hardware_reviews WHERE id IN (?,?,?) ORDER BY created_at,id", UUID.class,
                visible.get(0), visible.get(1), visible.get(2));
        when(audience.evaluate(viewer)).thenReturn(new HardwareReviewAudiencePolicy.Decision(true, Set.of(blocked)));
        mvc.perform(get(collection(product)).with(actor(viewer)).param("size", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(3)).andExpect(jsonPath("$.items[0].id").value(order.getFirst().toString()))
                .andExpect(jsonPath("$.summary.ratedReviews").value(2)).andExpect(jsonPath("$.summary.averageRating").value(4.0));
        mvc.perform(get(collection(product)).with(actor(viewer)).param("size", "1").param("page", "3"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items").isEmpty()).andExpect(jsonPath("$.totalElements").value(3));
        var blockedId = jdbc.queryForObject("SELECT id FROM hardware_reviews WHERE user_id=?", UUID.class, blocked);
        mvc.perform(get(item(blockedId)).with(actor(viewer))).andExpect(status().isNotFound());
    }

    @Test
    void unavailableAudienceNullFailureAndGlobalDenyKeepOnlyOwnerData() throws Exception {
        var own = create(viewer, product, "Own public text", 5);
        var foreign = create(owner, product, "Other public text", 1);
        when(audience.evaluate(viewer)).thenReturn(null);
        mvc.perform(get(collection(product)).with(actor(viewer))).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1)).andExpect(jsonPath("$.items[0].id").value(own.toString()));
        when(audience.evaluate(viewer)).thenThrow(new IllegalStateException("synthetic unavailable"));
        mvc.perform(get(item(foreign)).with(actor(viewer))).andExpect(status().isNotFound());
        doReturn(HardwareReviewAudiencePolicy.Decision.denyOthers()).when(audience).evaluate(viewer);
        mvc.perform(get(collection(product)).with(actor(viewer))).andExpect(jsonPath("$.summary.averageRating").value(5.0));
        assertThat(new UnavailableHardwareReviewAudiencePolicy().evaluate(viewer).allowOthers()).isFalse();
        var oversized = new HashSet<UUID>();
        for (int i = 0; i < 1001; i++) oversized.add(UUID.randomUUID());
        assertThatIllegalArgumentException().isThrownBy(() -> new HardwareReviewAudiencePolicy.Decision(true, oversized));
        assertThatIllegalArgumentException().isThrownBy(() -> new HardwareReviewAudiencePolicy.Decision(true, null));
    }

    @Test
    void archivedProductsHideGeneralReadsButOwnerCanReadAndDeleteHistory() throws Exception {
        var id = create(owner, product, "Historical text", 4);
        jdbc.update("UPDATE hardware_products SET status='ARCHIVED' WHERE id=?", product);
        mvc.perform(get(collection(product)).with(actor(owner))).andExpect(status().isNotFound());
        mvc.perform(get(item(id)).with(actor(viewer))).andExpect(status().isNotFound());
        mvc.perform(get(item(id)).with(actor(owner))).andExpect(status().isOk());
        mvc.perform(post(collection(product)).with(actor(viewer)).with(csrf()).contentType("application/json")
                .content(content("Cannot create", 5))).andExpect(status().isNotFound());
        mvc.perform(put(item(id)).with(actor(owner)).with(csrf()).contentType("application/json")
                .content("{\"reviewVersion\":0,\"body\":\"Cannot update\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(delete(item(id)).with(actor(owner)).with(csrf()).param("reviewVersion", "0"))
                .andExpect(status().isNoContent());
        mvc.perform(get(collection(UUID.randomUUID())).with(actor(owner))).andExpect(status().isNotFound());
        mvc.perform(get(item(UUID.randomUUID())).with(actor(owner))).andExpect(status().isNotFound());
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"body\":\" \"}", "{\"body\":\"<script>\"}", "{\"body\":\"＜script＞\"}",
        "{\"body\":\"Good\",\"rating\":0}", "{\"body\":\"Good\",\"rating\":6}",
        "{\"body\":\"Good\",\"userId\":\"fake\"}", "{\"body\":\"Good\",\"status\":\"VISIBLE\"}"})
    void rejectsInvalidUgcRatingsAndMassAssignmentWithNoPartialMutation(String body) throws Exception {
        mvc.perform(post(collection(product)).with(actor(owner)).with(csrf()).contentType("application/json").content(body))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM hardware_reviews", Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM hardware_write_limits", Long.class)).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"-1:1", "10001:1", "0:0", "0:101"})
    void boundsBothListEndpoints(String values) throws Exception {
        var parts = values.split(":");
        for (var path : List.of(collection(product), BASE + "/reviews/mine")) {
            mvc.perform(get(path).with(actor(owner)).param("page", parts[0]).param("size", parts[1]))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    void requiresTypedActorCsrfAndSmallBodyOnEveryMutation() throws Exception {
        var id = create(owner, product, "Security fixture", 4);
        for (var path : List.of(collection(product), item(id), BASE + "/reviews/mine")) {
            mvc.perform(get(path).header("X-User-Id", owner).header("X-Role", "ADMIN"))
                    .andExpect(status().isUnauthorized());
            mvc.perform(get(path).with(user("fake").roles("ADMIN"))).andExpect(status().isForbidden());
        }
        mvc.perform(post(collection(product)).with(actor(owner)).contentType("application/json").content(content("No csrf", 3)))
                .andExpect(status().isForbidden());
        mvc.perform(put(item(id)).with(actor(owner)).contentType("application/json").content("{}"))
                .andExpect(status().isForbidden());
        mvc.perform(delete(item(id)).with(actor(owner)).param("reviewVersion", "0")).andExpect(status().isForbidden());
        mvc.perform(post(collection(second)).with(actor(owner)).with(csrf()).contentType("application/json").content(" ".repeat(17000)))
                .andExpect(status().is(413));
        mvc.perform(put(item(id)).with(actor(owner)).with(csrf()).contentType("application/json")
                .content("{\"body\":\"Missing version\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void actorRateLimitIsAtomicAndDoesNotWriteTheRejectedReview() throws Exception {
        for (int i = 0; i < 20; i++) create(owner, product(), "Unique review " + i, null);
        mvc.perform(post(collection(second)).with(actor(owner)).with(csrf()).contentType("application/json")
                .content(content("Rejected by actor quota", null))).andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "60"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM hardware_reviews WHERE user_id=?", Long.class, owner)).isEqualTo(20);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM hardware_review_audit WHERE actor_id=?", Long.class, owner)).isEqualTo(20);
    }

    @Test
    void socketPeerLimitIgnoresForgedForwardingHeadersAndExpiresHashedBuckets() throws Exception {
        var key = "peer:" + HardwareReviewText.hash("127.0.0.1");
        jdbc.update("INSERT INTO hardware_write_limits(bucket_key,window_start,hits) SELECT ?,date_trunc('minute',now())+i*interval '1 minute',120 FROM generate_series(0,1) i", key);
        mvc.perform(post(collection(product)).with(actor(owner)).with(csrf()).header("X-Forwarded-For", "198.51.100.20")
                .contentType("application/json").content(content("Peer limited", null)))
                .andExpect(status().isTooManyRequests());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM hardware_reviews", Long.class)).isZero();
        jdbc.update("INSERT INTO hardware_write_limits VALUES ('expired-synthetic',now()-interval '3 minutes',1)");
        writes.clearExpiredLimits();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM hardware_write_limits WHERE bucket_key='expired-synthetic'", Long.class)).isZero();
        assertThat(jdbc.queryForList("SELECT bucket_key FROM hardware_write_limits", String.class))
                .allMatch(k -> !k.contains("127.0.0.1"));
    }

    @Test
    void appendOnlyAuditAndDatabaseChecksCannotBeBypassed() throws Exception {
        var id = create(owner, product, "Audit content", 4);
        assertThatThrownBy(() -> jdbc.update("UPDATE hardware_review_audit SET reason='changed' WHERE target_id=?", id))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("DELETE FROM hardware_review_audit WHERE target_id=?", id))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.execute("TRUNCATE hardware_review_audit"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE hardware_reviews SET rating=6 WHERE id=?", id))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE hardware_reviews SET status='INVALID' WHERE id=?", id))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE hardware_reviews SET status='DELETED' WHERE id=?", id))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void concurrentDuplicateCreatesHaveOneWinnerAndOneAuditRecord() throws Exception {
        var start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var results = new ArrayList<Future<Integer>>();
            for (int i = 0; i < 2; i++) {
                results.add(pool.submit(() -> {
                    start.await();
                    return mvc.perform(post(collection(product)).with(actor(owner)).with(csrf()).contentType("application/json")
                            .content(content("Concurrent duplicate", 4))).andReturn().getResponse().getStatus();
                }));
            }
            start.countDown();
            assertThat(List.of(results.get(0).get(), results.get(1).get())).containsExactlyInAnyOrder(201, 409);
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM hardware_reviews", Long.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM hardware_review_audit WHERE actor_id=?", Long.class, owner)).isEqualTo(1);
    }
}
