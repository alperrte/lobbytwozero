package com.ltz.hardware.api.controller;

import com.ltz.hardware.application.port.*;
import com.ltz.hardware.domain.event.HardwareReviewEvent;
import com.ltz.hardware.infrastructure.messaging.outbox.HardwareModerationOutbox;
import com.ltz.hardware.infrastructure.persistence.PostgresIntegrationSupport;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
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
class HardwareReviewModerationControllerTest extends PostgresIntegrationSupport {
    private static final String BASE = "/api/v1/hardware";
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired HardwareModerationOutbox outbox;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired jakarta.persistence.EntityManagerFactory entityManagerFactory;
    @MockitoBean HardwareReviewAudiencePolicy audience;
    UUID owner;
    UUID reporter;
    UUID moderator;
    UUID product;
    UUID review;

    @BeforeEach
    void prepare() throws Exception {
        owner = UUID.randomUUID();
        reporter = UUID.randomUUID();
        moderator = UUID.randomUUID();
        jdbc.update("DELETE FROM hardware_campaigns");
        jdbc.update("DELETE FROM hardware_profiles");
        jdbc.update("DELETE FROM hardware_product_brands");
        jdbc.update("DELETE FROM hardware_products");
        product = product();
        when(audience.evaluate(any())).thenReturn(new HardwareReviewAudiencePolicy.Decision(true, Set.of()));
        review = createReview(product, "Review private text");
    }

    private UUID product() {
        var id = UUID.randomUUID();
        jdbc.update("INSERT INTO hardware_products(id,category,model,normalized_model) VALUES (?,'GPU',?,?)",
                id, "Model " + id, "model " + id);
        return id;
    }
    private UUID createReview(UUID id, String body) throws Exception {
        var result = mvc.perform(post(BASE + "/catalog/products/" + id + "/reviews").with(actor(owner)).with(csrf())
                .contentType("application/json").content(json.writeValueAsString(Map.of("body", body, "rating", 4))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return UUID.fromString(json.readTree(result).get("id").asText());
    }
    private String reports(UUID id) { return BASE + "/reviews/" + id + "/reports"; }
    private String target() { return BASE + "/moderation/reviews/" + review; }
    private String action(String value, long version) {
        return json.writeValueAsString(Map.of("action", value, "reviewVersion", version, "reason", "Policy decision"));
    }
    private RequestPostProcessor permission(UUID id, String permission) {
        return authentication(new UsernamePasswordAuthenticationToken(new AuthenticatedActor(id), null,
                List.of(new SimpleGrantedAuthority(permission))));
    }
    private RequestPostProcessor moderator() { return permission(moderator, HardwareReviewModerationAccess.PERMISSION); }
    private UUID report(UUID author, UUID targetId) throws Exception {
        var result = mvc.perform(post(reports(targetId)).with(actor(author)).with(csrf()).contentType("application/json")
                .content("{\"reason\":\"SPAM\",\"details\":\"Private report details\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return UUID.fromString(json.readTree(result).get("id").asText());
    }
    private void seedPending(int count) {
        jdbc.update("""
            INSERT INTO outbox_events(id,aggregate_type,aggregate_id,event_type,event_version,payload,occurred_at)
            SELECT md5(i::text)::uuid,'HARDWARE_REVIEW',?,'ContentReported',1,'{}'::jsonb,now()
            FROM generate_series(1,?) i
            """, review, count);
    }

    @Test
    void reportAndPendingEnvelopeAreAtomicMinimalAndReporterScoped() throws Exception {
        var result = mvc.perform(post(reports(review)).with(actor(reporter)).with(csrf()).contentType("application/json")
                .content("{\"reason\":\"SPAM\",\"details\":\"Private report details\"}"))
                .andExpect(status().isCreated()).andExpect(header().exists("Location"))
                .andExpect(header().string("Cache-Control", "no-store, private"))
                .andExpect(jsonPath("$.outboxStatus").value("PENDING"))
                .andExpect(jsonPath("$.deliveryStatus").value("NOT_DELIVERED"))
                .andExpect(jsonPath("$.reporterId").doesNotExist()).andReturn().getResponse().getContentAsString();
        var id = UUID.fromString(json.readTree(result).get("id").asText());
        var payload = jdbc.queryForObject("SELECT payload::text FROM outbox_events", String.class);
        var event = json.readTree(payload);
        assertThat(event.get("producer").asText()).isEqualTo("hardware-service");
        assertThat(event.get("eventType").asText()).isEqualTo("ContentReported");
        assertThat(event.get("eventVersion").asInt()).isEqualTo(1);
        assertThat(event.get("payload").get("reportId").asText()).isEqualTo(id.toString());
        assertThat(payload).doesNotContain("Review private text", "Private report details", owner.toString(), reporter.toString());
        assertThat(jdbc.queryForObject("SELECT attempts FROM outbox_events", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT sent_at FROM outbox_events", java.sql.Timestamp.class)).isNull();
        mvc.perform(get(BASE + "/review-reports/" + id).with(actor(reporter))).andExpect(status().isOk());
        mvc.perform(get(BASE + "/review-reports/" + id).with(actor(owner))).andExpect(status().isNotFound());
        mvc.perform(get(BASE + "/review-reports/" + UUID.randomUUID()).with(actor(reporter))).andExpect(status().isNotFound());
        mvc.perform(get(BASE + "/review-reports/" + id)).andExpect(status().isUnauthorized());
    }

    @Test
    void duplicateReportNeverCreatesASecondEventOrAudit() throws Exception {
        report(reporter, review);
        mvc.perform(post(reports(review)).with(actor(reporter)).with(csrf()).contentType("application/json")
                .content("{\"reason\":\"HARASSMENT\"}")).andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM hardware_review_reports", Long.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM outbox_events", Long.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM hardware_review_audit WHERE target_id=? AND action='REPORT_REVIEW'", Long.class, review)).isEqualTo(1);
    }

    @Test
    void selfHiddenDeletedArchivedAndMissingTargetsCannotBeReported() throws Exception {
        mvc.perform(post(reports(review)).with(actor(owner)).with(csrf()).contentType("application/json")
                .content("{\"reason\":\"SPAM\"}")).andExpect(status().isBadRequest());
        jdbc.update("UPDATE hardware_reviews SET status='HIDDEN' WHERE id=?", review);
        mvc.perform(post(reports(review)).with(actor(reporter)).with(csrf()).contentType("application/json")
                .content("{\"reason\":\"SPAM\"}")).andExpect(status().isNotFound());
        jdbc.update("UPDATE hardware_reviews SET status='VISIBLE' WHERE id=?", review);
        jdbc.update("UPDATE hardware_products SET status='ARCHIVED' WHERE id=?", product);
        mvc.perform(post(reports(review)).with(actor(reporter)).with(csrf()).contentType("application/json")
                .content("{\"reason\":\"SPAM\"}")).andExpect(status().isNotFound());
        mvc.perform(delete(BASE + "/reviews/" + review).with(actor(owner)).with(csrf()).param("reviewVersion", "0"))
                .andExpect(status().isNoContent());
        mvc.perform(post(reports(review)).with(actor(reporter)).with(csrf()).contentType("application/json")
                .content("{\"reason\":\"SPAM\"}")).andExpect(status().isNotFound());
        mvc.perform(post(reports(UUID.randomUUID())).with(actor(reporter)).with(csrf()).contentType("application/json")
                .content("{\"reason\":\"SPAM\"}")).andExpect(status().isNotFound());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM outbox_events", Long.class)).isZero();
    }

    @Test
    void audienceFailureAndBlocksDenyReportsWithoutPartialRows() throws Exception {
        when(audience.evaluate(reporter)).thenReturn(null);
        mvc.perform(post(reports(review)).with(actor(reporter)).with(csrf()).contentType("application/json")
                .content("{\"reason\":\"SPAM\"}")).andExpect(status().isNotFound());
        doThrow(new IllegalStateException("synthetic unavailable")).when(audience).evaluate(reporter);
        mvc.perform(post(reports(review)).with(actor(reporter)).with(csrf()).contentType("application/json")
                .content("{\"reason\":\"SPAM\"}")).andExpect(status().isNotFound());
        doReturn(new HardwareReviewAudiencePolicy.Decision(true, Set.of(owner))).when(audience).evaluate(reporter);
        mvc.perform(post(reports(review)).with(actor(reporter)).with(csrf()).contentType("application/json")
                .content("{\"reason\":\"SPAM\"}")).andExpect(status().isNotFound());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM hardware_review_reports", Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM outbox_events", Long.class)).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"reason\":\"INVALID\"}", "{\"reason\":\"SPAM\",\"details\":\" \"}",
        "{\"reason\":\"SPAM\",\"details\":\"<script>\"}", "{\"reason\":\"SPAM\",\"details\":\"＜script＞\"}",
        "{\"reason\":\"SPAM\",\"userId\":\"fake\"}"})
    void malformedReportRollsBackEvenWhenOutboxWasStaged(String body) throws Exception {
        mvc.perform(post(reports(review)).with(actor(reporter)).with(csrf()).contentType("application/json").content(body))
                .andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM hardware_review_reports", Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM outbox_events", Long.class)).isZero();
    }

    @Test
    void moderationRequiresPermissionAtTheServiceBoundaryAndCsrfAtHttpBoundary() throws Exception {
        for (var requestActor : List.of(actor(owner), permission(owner, CatalogWriteAccess.PERMISSION),
                permission(owner, "ROLE_ADMIN"), user("untrusted").authorities(new SimpleGrantedAuthority(HardwareReviewModerationAccess.PERMISSION)))) {
            mvc.perform(get(target()).with(requestActor)).andExpect(status().isForbidden());
            mvc.perform(post(target() + "/actions").with(requestActor).with(csrf()).contentType("application/json")
                    .content(action("HIDE", 0))).andExpect(status().isForbidden());
        }
        mvc.perform(get(target()).header("X-User-Id", moderator).header("X-Role", "ADMIN")).andExpect(status().isUnauthorized());
        mvc.perform(post(target() + "/actions").with(moderator()).contentType("application/json").content(action("HIDE", 0)))
                .andExpect(status().isForbidden());
        mvc.perform(post(reports(review)).with(actor(reporter)).contentType("application/json").content("{\"reason\":\"SPAM\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(post(reports(review)).with(csrf()).contentType("application/json").content("{\"reason\":\"SPAM\"}"))
                .andExpect(status().isUnauthorized());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM outbox_events", Long.class)).isZero();
    }

    @Test
    void hideRestoreAuditAndPendingNoticeRemainConsistentWithPublicCounters() throws Exception {
        report(reporter, review);
        mvc.perform(post(target() + "/actions").with(moderator()).with(csrf()).contentType("application/json").content(action("HIDE", 0)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.review.status").value("HIDDEN"))
                .andExpect(jsonPath("$.review.version").value(1)).andExpect(jsonPath("$.notificationDeliveryStatus").value("NOT_DELIVERED"));
        mvc.perform(get(BASE + "/catalog/products/" + product + "/reviews").with(actor(reporter)))
                .andExpect(jsonPath("$.totalElements").value(0)).andExpect(jsonPath("$.summary.averageRating").isEmpty());
        mvc.perform(get(BASE + "/reviews/" + review).with(actor(owner))).andExpect(status().isOk());
        mvc.perform(get(BASE + "/reviews/" + review).with(actor(reporter))).andExpect(status().isNotFound());
        mvc.perform(put(BASE + "/reviews/" + review).with(actor(owner)).with(csrf()).contentType("application/json")
                .content("{\"reviewVersion\":1,\"body\":\"Owner bypass attempt\"}"))
                .andExpect(status().isConflict());
        mvc.perform(post(target() + "/actions").with(moderator()).with(csrf()).contentType("application/json").content(action("RESTORE", 1)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.review.status").value("VISIBLE"))
                .andExpect(jsonPath("$.review.version").value(2));
        mvc.perform(get(BASE + "/catalog/products/" + product + "/reviews").with(actor(reporter)))
                .andExpect(jsonPath("$.totalElements").value(1)).andExpect(jsonPath("$.summary.averageRating").value(4.0));
        mvc.perform(post(target() + "/actions").with(moderator()).with(csrf()).contentType("application/json").content(action("HIDE", 1)))
                .andExpect(status().isConflict());
        mvc.perform(post(target() + "/actions").with(moderator()).with(csrf()).contentType("application/json").content(action("RESTORE", 2)))
                .andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM outbox_events", Long.class)).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM outbox_events WHERE status<>'PENDING' OR sent_at IS NOT NULL", Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM hardware_review_audit WHERE actor_id=? AND reason='Policy decision'", Long.class, moderator)).isEqualTo(2);
        assertThat(jdbc.queryForList("SELECT payload::text FROM outbox_events", String.class))
                .allMatch(p -> !p.contains("Review private text") && !p.contains("Private report details"));
        mvc.perform(delete(BASE + "/reviews/" + review).with(actor(owner)).with(csrf()).param("reviewVersion", "2"))
                .andExpect(status().isNoContent());
        mvc.perform(post(target() + "/actions").with(moderator()).with(csrf()).contentType("application/json").content(action("RESTORE", 3)))
                .andExpect(status().isNotFound());
        mvc.perform(get(target()).with(moderator())).andExpect(status().isOk()).andExpect(jsonPath("$.review.body").isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"reviewVersion\":0,\"action\":\"REMOVE\",\"reason\":\"Reason\"}",
        "{\"reviewVersion\":0,\"action\":\"HIDE\",\"reason\":\" \"}",
        "{\"reviewVersion\":0,\"action\":\"HIDE\",\"reason\":\"＜script＞\"}",
        "{\"reviewVersion\":-1,\"action\":\"HIDE\",\"reason\":\"Reason\"}",
        "{\"reviewVersion\":0,\"action\":\"HIDE\",\"reason\":\"Reason\",\"role\":\"ADMIN\"}"})
    void invalidModerationNeverChangesTargetOrQueuesNotice(String body) throws Exception {
        mvc.perform(post(target() + "/actions").with(moderator()).with(csrf()).contentType("application/json").content(body))
                .andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("SELECT status FROM hardware_reviews WHERE id=?", String.class, review)).isEqualTo("VISIBLE");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM outbox_events", Long.class)).isZero();
    }

    @Test
    void moderationQuotaAllowsSixtiethWriteAndRejectsTheNextWithoutStateChange() throws Exception {
        jdbc.update("INSERT INTO hardware_write_limits(bucket_key,window_start,hits) SELECT ?,date_trunc('minute',now())+i*interval '1 minute',59 FROM generate_series(0,1) i", "moderator:" + moderator);
        mvc.perform(post(target() + "/actions").with(moderator()).with(csrf()).contentType("application/json").content(action("HIDE", 0)))
                .andExpect(status().isOk());
        mvc.perform(post(target() + "/actions").with(moderator()).with(csrf()).contentType("application/json").content(action("RESTORE", 1)))
                .andExpect(status().isTooManyRequests()).andExpect(header().string("Retry-After", "60"));
        assertThat(jdbc.queryForObject("SELECT status FROM hardware_reviews WHERE id=?", String.class, review)).isEqualTo("HIDDEN");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM outbox_events", Long.class)).isEqualTo(1);
    }

    @Test
    void fullOutboxRollsBackReportAuditAndQuota() throws Exception {
        seedPending(10000);
        mvc.perform(post(reports(review)).with(actor(reporter)).with(csrf()).contentType("application/json")
                .content("{\"reason\":\"SPAM\"}")).andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("MODERATION_DELIVERY_UNAVAILABLE"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM hardware_review_reports", Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM hardware_review_audit WHERE actor_id=?", Long.class, reporter)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM hardware_write_limits WHERE bucket_key=?", Long.class, "actor:" + reporter)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM outbox_events", Long.class)).isEqualTo(10000);
    }

    @Test
    void fullOutboxRollsBackAlreadyFlushedModerationAndAudit() throws Exception {
        seedPending(10000);
        mvc.perform(post(target() + "/actions").with(moderator()).with(csrf()).contentType("application/json").content(action("HIDE", 0)))
                .andExpect(status().isServiceUnavailable());
        assertThat(jdbc.queryForObject("SELECT status FROM hardware_reviews WHERE id=?", String.class, review)).isEqualTo("VISIBLE");
        assertThat(jdbc.queryForObject("SELECT version FROM hardware_reviews WHERE id=?", Long.class, review)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM hardware_review_audit WHERE actor_id=?", Long.class, moderator)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM outbox_events", Long.class)).isEqualTo(10000);
    }

    @Test
    void concurrentReportsRemainUniqueAndOutboxCapacityIsSerializedAcrossTargets() throws Exception {
        var start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var first = pool.submit(() -> reportStatus(start, reporter, review));
            var duplicate = pool.submit(() -> reportStatus(start, reporter, review));
            start.countDown();
            assertThat(List.of(first.get(), duplicate.get())).containsExactlyInAnyOrder(201, 409);
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM hardware_review_reports", Long.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM outbox_events", Long.class)).isEqualTo(1);
    }

    private int reportStatus(CountDownLatch start, UUID actorId, UUID targetId) throws Exception {
        return reportStatus(start, actorId, targetId, "127.0.0.1");
    }

    private int reportStatus(CountDownLatch start, UUID actorId, UUID targetId, String peer) throws Exception {
        start.await();
        return mvc.perform(post(reports(targetId)).with(actor(actorId)).with(csrf()).contentType("application/json")
                .with(request -> { request.setRemoteAddr(peer); return request; })
                .content("{\"reason\":\"SPAM\"}")).andReturn().getResponse().getStatus();
    }

    @Test
    void concurrentDifferentTargetsCannotOverflowPendingBudget() throws Exception {
        var another = createReview(product(), "Second target review");
        seedPending(9999);
        var otherReporter = UUID.randomUUID();
        var start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var first = pool.submit(() -> reportStatus(start, reporter, review, "198.51.100.10"));
            var second = pool.submit(() -> reportStatus(start, otherReporter, another, "198.51.100.11"));
            start.countDown();
            assertThat(List.of(first.get(), second.get())).containsExactlyInAnyOrder(201, 503);
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM outbox_events", Long.class)).isEqualTo(10000);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM hardware_review_reports", Long.class)).isEqualTo(1);
    }

    @Test
    void concurrentOwnerEditAndHideUseTheSameReviewVersion() throws Exception {
        var start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var hidden = pool.submit(() -> {
                start.await();
                return mvc.perform(post(target() + "/actions").with(moderator()).with(csrf()).contentType("application/json")
                        .content(action("HIDE", 0))).andReturn().getResponse().getStatus();
            });
            var edited = pool.submit(() -> {
                start.await();
                return mvc.perform(put(BASE + "/reviews/" + review).with(actor(owner)).with(csrf()).contentType("application/json")
                        .content("{\"reviewVersion\":0,\"body\":\"Race update\"}")).andReturn().getResponse().getStatus();
            });
            start.countDown();
            assertThat(List.of(hidden.get(), edited.get())).containsExactlyInAnyOrder(200, 409);
        }
        assertThat(jdbc.queryForObject("SELECT version FROM hardware_reviews WHERE id=?", Long.class, review)).isEqualTo(1);
    }

    @Test
    void moderationReportPageUsesBoundedQueriesAndNeverExposesReporterIds() throws Exception {
        for (int i = 0; i < 25; i++) report(UUID.randomUUID(), review);
        var statistics = entityManagerFactory.unwrap(org.hibernate.SessionFactory.class).getStatistics();
        statistics.setStatisticsEnabled(true);
        statistics.clear();
        try {
            var result = mvc.perform(get(target()).with(moderator()).param("size", "1"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.totalReports").value(25))
                    .andExpect(jsonPath("$.totalPages").value(25)).andExpect(jsonPath("$.reports.length()").value(1))
                    .andExpect(jsonPath("$.reports[0].reporterId").doesNotExist()).andReturn().getResponse().getContentAsString();
            assertThat(result).doesNotContain(reporter.toString());
            assertThat(statistics.getPrepareStatementCount()).isEqualTo(3);
        } finally {
            statistics.setStatisticsEnabled(false);
        }
        mvc.perform(get(target()).with(moderator()).param("page", "25").param("size", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.reports").isEmpty());
        mvc.perform(get(BASE + "/moderation/reviews/" + UUID.randomUUID()).with(moderator())).andExpect(status().isNotFound());
    }

    @ParameterizedTest
    @ValueSource(strings = {"-1:1", "10001:1", "0:0", "0:101"})
    void boundsModerationReportPage(String input) throws Exception {
        var values = input.split(":");
        mvc.perform(get(target()).with(moderator()).param("page", values[0]).param("size", values[1]))
                .andExpect(status().isBadRequest());
    }

    @Test
    void purgedOrLocallyMarkedEventsNeverClaimVerifiedConsumerDelivery() throws Exception {
        var id = report(reporter, review);
        jdbc.update("UPDATE outbox_events SET status='SENT'");
        mvc.perform(get(BASE + "/review-reports/" + id).with(actor(reporter))).andExpect(status().isOk())
                .andExpect(jsonPath("$.outboxStatus").value("SENT")).andExpect(jsonPath("$.deliveryStatus").value("NOT_VERIFIED"));
        jdbc.update("DELETE FROM outbox_events");
        mvc.perform(get(BASE + "/review-reports/" + id).with(actor(reporter))).andExpect(status().isOk())
                .andExpect(jsonPath("$.outboxStatus").value("NOT_AVAILABLE")).andExpect(jsonPath("$.deliveryStatus").value("NOT_VERIFIED"));
        mvc.perform(get(target()).with(moderator())).andExpect(status().isOk())
                .andExpect(jsonPath("$.reports[0].outboxStatus").value("NOT_AVAILABLE"));
    }

    @Test
    void oversizedLocalEventFailsBeforeAnyOutboxWrite() {
        var event = new HardwareReviewEvent<>(UUID.randomUUID(), "ContentReported", 1, Instant.now(), "hardware-service", review, "x".repeat(5000));
        var transaction = new TransactionTemplate(transactionManager);
        java.util.function.Consumer<org.springframework.transaction.TransactionStatus> append = ignored -> outbox.append(event);
        assertThatThrownBy(() -> transaction.executeWithoutResult(append))
                .isInstanceOf(org.springframework.dao.InvalidDataAccessApiUsageException.class)
                .hasRootCauseInstanceOf(IllegalArgumentException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM outbox_events", Long.class)).isZero();
    }

    @Test
    void archivedProductCannotBeRestoredToPublicVisibility() throws Exception {
        mvc.perform(post(target() + "/actions").with(moderator()).with(csrf()).contentType("application/json").content(action("HIDE", 0)))
                .andExpect(status().isOk());
        jdbc.update("UPDATE hardware_products SET status='ARCHIVED' WHERE id=?", product);
        mvc.perform(post(target() + "/actions").with(moderator()).with(csrf()).contentType("application/json").content(action("RESTORE", 1)))
                .andExpect(status().isNotFound());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM outbox_events", Long.class)).isEqualTo(1);
    }
}
