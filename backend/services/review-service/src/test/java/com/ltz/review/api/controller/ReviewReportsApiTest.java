package com.ltz.review.api.controller;

import com.ltz.review.application.port.GameCatalogPort;
import com.ltz.review.application.service.AccountErasureService;
import java.time.OffsetDateTime;
import java.util.ArrayList;
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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import static com.ltz.review.api.controller.RatingApiTest.actor;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** REV-07 (Review tarafı): rapor oluşturma, mükerrer engeli, sahiplik politikası, gizlilik ve hesap silme etkileşimi. */
@SpringBootTest
class ReviewReportsApiTest {
    @Autowired WebApplicationContext context;
    @Autowired JdbcClient jdbc;
    @Autowired AccountErasureService erasure;
    @MockitoBean GameCatalogPort games;
    MockMvc mvc;
    UUID reporter;
    UUID author;
    UUID review;

    @BeforeEach void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(context).defaultRequest(get("/").with(csrf())).apply(springSecurity()).build();
        reporter = UUID.randomUUID();
        author = UUID.randomUUID();
        review = seed(author, "2026-01-01T00:00:00Z", true);
    }

    UUID seed(UUID owner, String date, boolean visible) {
        UUID id = UUID.randomUUID();
        var time = OffsetDateTime.parse(date);
        jdbc.sql("""
                INSERT INTO reviews (id,user_id,game_id,body,status,created_at,updated_at,deleted_at)
                VALUES (?,?,?,?,?,?,?,?)
                """).params(id, owner, UUID.randomUUID(), visible ? "Deneyim" : null, visible ? "VISIBLE" : "DELETED",
                        time, time, visible ? null : time).update();
        return id;
    }

    String path(UUID target) { return "/api/v1/review/reviews/" + target + "/reports"; }

    ResultActions report(UUID who, UUID target, String json) throws Exception {
        return mvc.perform(post(path(target)).with(actor(who)).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    long count(UUID target) {
        return jdbc.sql("SELECT count(*) FROM review_reports WHERE review_id = ?").param(target).query(Long.class).single();
    }

    @Test void createsAReportWithoutExposingAnyIdentity() throws Exception {
        report(reporter, review, "{\"reason\":\"SPAM\",\"detail\":\"  Reklam içeriyor \"}").andExpect(status().isCreated())
                .andExpect(header().string("Location", path(review) + "/mine"))
                .andExpect(jsonPath("$.reviewId").value(review.toString()))
                .andExpect(jsonPath("$.reason").value("SPAM"))
                .andExpect(jsonPath("$.detail").value("Reklam içeriyor"))
                .andExpect(jsonPath("$.status").value("RECEIVED"))
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.reporterId").doesNotExist())
                .andExpect(jsonPath("$.userId").doesNotExist())
                .andExpect(jsonPath("$.authorId").doesNotExist());
        assertEquals(1, count(review));
        verifyNoInteractions(games);
    }

    @Test void detailIsOptionalAndBlankDetailIsStoredAsNull() throws Exception {
        report(reporter, review, "{\"reason\":\"OTHER\"}").andExpect(status().isCreated()).andExpect(jsonPath("$.detail").doesNotExist());
        UUID other = seed(author, "2026-01-02T00:00:00Z", true);
        report(reporter, other, "{\"reason\":\"OFF_TOPIC\",\"detail\":\"   \"}").andExpect(status().isCreated())
                .andExpect(jsonPath("$.detail").doesNotExist());
    }

    @Test void aSecondReportByTheSameReporterIsRejectedWithAMachineReadableCode() throws Exception {
        report(reporter, review, "{\"reason\":\"SPAM\"}").andExpect(status().isCreated());
        report(reporter, review, "{\"reason\":\"HARASSMENT\",\"detail\":\"Başka gerekçe\"}").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REPORT_ALREADY_EXISTS")).andExpect(jsonPath("$.status").value(409));
        assertEquals(1, count(review));
        mvc.perform(get(path(review) + "/mine").with(actor(reporter))).andExpect(jsonPath("$.reason").value("SPAM"));
    }

    @Test void differentReportersMayReportTheSameReview() throws Exception {
        report(reporter, review, "{\"reason\":\"SPAM\"}").andExpect(status().isCreated());
        report(UUID.randomUUID(), review, "{\"reason\":\"SPAM\"}").andExpect(status().isCreated());
        assertEquals(2, count(review));
    }

    @Test void concurrentDuplicateRequestsProduceExactlyOneReport() throws Exception {
        try (var pool = Executors.newFixedThreadPool(8)) {
            List<Callable<Integer>> calls = new ArrayList<>();
            for (int i = 0; i < 8; i++) calls.add(() -> report(reporter, review, "{\"reason\":\"SPAM\"}").andReturn().getResponse().getStatus());
            int created = 0;
            int conflicts = 0;
            for (var future : pool.invokeAll(calls)) {
                int code = future.get();
                if (code == 201) created++;
                else if (code == 409) conflicts++;
            }
            assertEquals(1, created);
            assertEquals(7, conflicts);
            assertEquals(1, count(review));
        }
    }

    @Test void theAuthorCannotReportTheirOwnReview() throws Exception {
        report(author, review, "{\"reason\":\"SPAM\"}").andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("REPORT_OWN_REVIEW"));
        assertEquals(0, count(review));
    }

    @Test void onlyVisibleExistingReviewsCanBeReported() throws Exception {
        report(reporter, seed(author, "2026-01-03T00:00:00Z", false), "{\"reason\":\"SPAM\"}").andExpect(status().isNotFound());
        report(reporter, UUID.randomUUID(), "{\"reason\":\"SPAM\"}").andExpect(status().isNotFound());
    }

    @Test void rejectsInvalidBodiesAndIdentifiers() throws Exception {
        for (String body : List.of("{}", "{\"reason\":null}", "{\"reason\":\"UNKNOWN\"}",
                "{\"reason\":\"SPAM\",\"detail\":\"" + "a".repeat(501) + "\"}",
                "{\"reason\":\"SPAM\",\"detail\":\"kötü\\u0000metin\"}",
                "{\"reason\":\"SPAM\",\"reporterId\":\"" + author + "\"}", "{\"reason\":1}", "not-json")) {
            report(reporter, review, body).andExpect(status().isBadRequest());
        }
        mvc.perform(post("/api/v1/review/reviews/not-a-uuid/reports").with(actor(reporter)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"SPAM\"}")).andExpect(status().isBadRequest());
        assertEquals(0, count(review));
    }

    @Test void anonymousCallersAreRejected() throws Exception {
        mvc.perform(post(path(review)).contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"SPAM\"}")).andExpect(status().isUnauthorized());
        mvc.perform(get(path(review) + "/mine")).andExpect(status().isUnauthorized());
    }

    @Test void reportersSeeOnlyTheirOwnReport() throws Exception {
        mvc.perform(get(path(review) + "/mine").with(actor(reporter))).andExpect(status().isNotFound());
        report(reporter, review, "{\"reason\":\"SPOILER\"}").andExpect(status().isCreated());
        mvc.perform(get(path(review) + "/mine").with(actor(reporter))).andExpect(status().isOk())
                .andExpect(jsonPath("$.reason").value("SPOILER")).andExpect(jsonPath("$.status").value("RECEIVED"));
        mvc.perform(get(path(review) + "/mine").with(actor(UUID.randomUUID()))).andExpect(status().isNotFound());
        mvc.perform(get(path(review) + "/mine").with(actor(author))).andExpect(status().isNotFound());
    }

    @Test void reportsNeverLeakIntoReviewResponses() throws Exception {
        report(reporter, review, "{\"reason\":\"SPAM\"}").andExpect(status().isCreated());
        mvc.perform(get("/api/v1/review/reviews/" + review).with(actor(author))).andExpect(status().isOk())
                .andExpect(jsonPath("$.reports").doesNotExist()).andExpect(jsonPath("$.reportCount").doesNotExist());
    }

    @Test void erasingTheReporterRemovesTheirReportsAndBlocksNewOnes() throws Exception {
        report(reporter, review, "{\"reason\":\"SPAM\"}").andExpect(status().isCreated());
        erasure.erase(reporter);
        assertEquals(0, count(review));
        report(reporter, review, "{\"reason\":\"SPAM\"}").andExpect(status().isForbidden());
        assertEquals(0, count(review));
    }

    @Test void erasingTheAuthorRemovesReportsAboutTheirReviews() throws Exception {
        report(reporter, review, "{\"reason\":\"SPAM\"}").andExpect(status().isCreated());
        erasure.erase(author);
        assertEquals(0, count(review));
    }

    @Test void aSoftDeletedReviewKeepsItsReportsButCannotBeReportedAgain() throws Exception {
        report(reporter, review, "{\"reason\":\"SPAM\"}").andExpect(status().isCreated());
        jdbc.sql("UPDATE reviews SET body = NULL, status = 'DELETED', deleted_at = clock_timestamp(), updated_at = clock_timestamp() WHERE id = ?")
                .param(review).update();
        assertEquals(1, count(review));
        report(UUID.randomUUID(), review, "{\"reason\":\"SPAM\"}").andExpect(status().isNotFound());
        mvc.perform(get(path(review) + "/mine").with(actor(reporter))).andExpect(status().isOk());
    }
}
