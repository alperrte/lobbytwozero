package com.ltz.review.api.controller;

import com.ltz.review.application.port.GameCatalogPort;
import com.ltz.review.application.service.AccountErasureService;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import static com.ltz.review.api.controller.RatingApiTest.actor;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** REV-15/REV-07: moderatör uçları, görünürlük, yazar yolları, rapor kapatma ve denetim kaydı (PostgreSQL). */
@SpringBootTest
class AdminModerationApiTest {
    private static final String ADMIN = "/api/v1/review/admin";
    @Autowired WebApplicationContext context;
    @Autowired JdbcClient jdbc;
    @Autowired AccountErasureService erasure;
    @MockitoBean GameCatalogPort games;
    MockMvc mvc;
    UUID moderator;
    UUID author;
    UUID reader;
    UUID game;
    UUID review;

    static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor as(UUID id, String role) {
        return jwt().jwt(token -> token.subject(id.toString()).claim("roles", List.of(role))).authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }

    @BeforeEach void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(context).defaultRequest(get("/").with(csrf())).apply(springSecurity()).build();
        moderator = UUID.randomUUID();
        author = UUID.randomUUID();
        reader = UUID.randomUUID();
        game = UUID.randomUUID();
        // Ortak test veritabanında diğer testlerin raporları genel liste toplamlarına karışmasın.
        jdbc.sql("DELETE FROM review_reports").update();
        review = seed(author, game, "2026-01-01T00:00:00Z");
    }

    UUID seed(UUID owner, UUID target, String date) {
        UUID id = UUID.randomUUID();
        var time = OffsetDateTime.parse(date);
        jdbc.sql("INSERT INTO reviews (id,user_id,game_id,body,status,created_at,updated_at) VALUES (?,?,?,?,?,?,?)")
                .params(id, owner, target, "Deneyim", "VISIBLE", time, time).update();
        return id;
    }

    void reportBy(UUID reporter, UUID target, String reason, String when) {
        var time = OffsetDateTime.parse(when);
        jdbc.sql("INSERT INTO review_reports (id,review_id,reporter_id,reason,created_at,updated_at) VALUES (?,?,?,?,?,?)")
                .params(UUID.randomUUID(), target, reporter, reason, time, time).update();
    }

    String statusOf(UUID target) { return jdbc.sql("SELECT status FROM reviews WHERE id = ?").param(target).query(String.class).single(); }

    long versionOf(UUID target) { return jdbc.sql("SELECT version FROM reviews WHERE id = ?").param(target).query(Long.class).single(); }

    ResultActions moderate(UUID target, String action, long version) throws Exception {
        return mvc.perform(post(ADMIN + "/reviews/" + target + "/moderation").with(as(moderator, "MODERATOR"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"action\":\"" + action + "\",\"reason\":\"Kural ihlali\",\"version\":" + version + "}"));
    }

    @Test void onlyModeratorsAndAdminsMayUseTheAdminEndpoints() throws Exception {
        String body = "{\"action\":\"HIDE\",\"reason\":\"x\",\"version\":0}";
        for (String role : List.of("PLAYER", "CONTENT_CREATOR", "GAME_DEVELOPER")) {
            mvc.perform(get(ADMIN + "/reports").with(as(reader, role))).andExpect(status().isForbidden());
            mvc.perform(get(ADMIN + "/reviews/" + review).with(as(reader, role))).andExpect(status().isForbidden());
            mvc.perform(get(ADMIN + "/reviews/" + review + "/reports").with(as(reader, role))).andExpect(status().isForbidden());
            mvc.perform(post(ADMIN + "/reviews/" + review + "/moderation").with(as(reader, role)).contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isForbidden());
            mvc.perform(post(ADMIN + "/reviews/" + review + "/reports/close").with(as(reader, role)).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"outcome\":\"REVIEWED\",\"reason\":\"x\"}")).andExpect(status().isForbidden());
        }
        mvc.perform(get(ADMIN + "/reports")).andExpect(status().isUnauthorized());
        mvc.perform(post(ADMIN + "/reviews/" + review + "/moderation").contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isUnauthorized());
        assertEquals("VISIBLE", statusOf(review));
        mvc.perform(get(ADMIN + "/reports").with(as(moderator, "MODERATOR"))).andExpect(status().isOk());
        mvc.perform(get(ADMIN + "/reports").with(as(moderator, "ADMIN"))).andExpect(status().isOk());
    }

    @Test void hidingRemovesTheReviewFromEveryPublicPathButTheAuthorStillSeesItsStatus() throws Exception {
        UUID liker = UUID.randomUUID();
        mvc.perform(put("/api/v1/review/reviews/" + review + "/like").with(actor(liker))).andExpect(status().isOk());
        moderate(review, "HIDE", 0).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("HIDDEN"))
                .andExpect(jsonPath("$.version").value(1)).andExpect(jsonPath("$.body").value("Deneyim"))
                .andExpect(jsonPath("$.history[0].action").value("HIDE")).andExpect(jsonPath("$.history[0].reason").value("Kural ihlali"))
                .andExpect(jsonPath("$.userId").doesNotExist()).andExpect(jsonPath("$.history[0].actorId").doesNotExist());
        String featured = "/api/v1/review/games/" + game + "/reviews/featured";
        mvc.perform(get(featured).with(actor(reader))).andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get("/api/v1/review/reviews/" + review).with(actor(reader))).andExpect(status().isNotFound());
        mvc.perform(put("/api/v1/review/reviews/" + review + "/like").with(actor(reader))).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/review/reviews/" + review + "/like").with(actor(liker))).andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/review/reviews/" + review + "/reports").with(actor(reader)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"SPAM\"}")).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/review/games/" + game + "/statistics").with(actor(reader))).andExpect(jsonPath("$.reviews.activeCount").value(0));
        // Yazar kendi gizli incelemesini ve durumunu görür.
        mvc.perform(get("/api/v1/review/reviews/" + review).with(actor(author))).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("HIDDEN")).andExpect(jsonPath("$.ownedByActor").value(true)).andExpect(jsonPath("$.version").value(1));
        mvc.perform(get("/api/v1/review/games/" + game + "/reviews/mine").with(actor(author))).andExpect(jsonPath("$.status").value("HIDDEN"));
        mvc.perform(get("/api/v1/review/reviews/mine").with(actor(author))).andExpect(jsonPath("$.items[0].status").value("HIDDEN"));
        mvc.perform(get("/api/v1/review/games/" + game + "/reviews/mine").with(actor(reader))).andExpect(status().isNotFound());
        // Geri alınınca beğeniler korunmuş olarak döner.
        moderate(review, "RESTORE", 1).andExpect(jsonPath("$.status").value("VISIBLE")).andExpect(jsonPath("$.version").value(2));
        mvc.perform(get(featured).with(actor(reader))).andExpect(jsonPath("$.items[0].likeCount").value(1))
                .andExpect(jsonPath("$.items[0].review.status").value("VISIBLE"));
        mvc.perform(get("/api/v1/review/games/" + game + "/statistics").with(actor(reader))).andExpect(jsonPath("$.reviews.activeCount").value(1));
    }

    @Test void theAuthorUserIdIsVisibleToAuthenticatedReadersButNeverOnModeratorReportOrAuditViews() throws Exception {
        mvc.perform(get("/api/v1/review/reviews/" + review).with(actor(reader))).andExpect(status().isOk())
                .andExpect(jsonPath("$.authorUserId").value(author.toString())).andExpect(jsonPath("$.ownedByActor").value(false));
        mvc.perform(get("/api/v1/review/reviews/" + review).with(actor(author))).andExpect(jsonPath("$.authorUserId").value(author.toString()));
        mvc.perform(get("/api/v1/review/reviews/" + review)).andExpect(status().isUnauthorized());
        reportBy(UUID.randomUUID(), review, "SPAM", "2026-03-01T00:00:00Z");
        mvc.perform(get(ADMIN + "/reviews/" + review).with(as(moderator, "MODERATOR"))).andExpect(jsonPath("$.authorUserId").doesNotExist());
        mvc.perform(get(ADMIN + "/reports").with(as(moderator, "MODERATOR"))).andExpect(jsonPath("$.items[0].authorUserId").doesNotExist());
        mvc.perform(get(ADMIN + "/reviews/" + review + "/reports").with(as(moderator, "MODERATOR"))).andExpect(jsonPath("$[0].authorUserId").doesNotExist());
        // Gizlenen inceleme: yalnız sahibine okunur; kimlik başkalarına sızmaz.
        moderate(review, "HIDE", 0).andExpect(status().isOk());
        mvc.perform(get("/api/v1/review/reviews/" + review).with(actor(reader))).andExpect(status().isNotFound());
    }

    @Test void moderationNeverTouchesTheIndependentRatingAndRecommendation() throws Exception {
        mvc.perform(put("/api/v1/review/games/" + game + "/rating").with(actor(author)).contentType(MediaType.APPLICATION_JSON).content("{\"rating\":9}"))
                .andExpect(status().isOk());
        mvc.perform(put("/api/v1/review/games/" + game + "/recommendation").with(actor(author)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"recommended\":true}")).andExpect(status().isOk());
        moderate(review, "ARCHIVE", 0).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ARCHIVED"));
        mvc.perform(get("/api/v1/review/games/" + game + "/rating-summary").with(actor(reader))).andExpect(jsonPath("$.averageRating").value(9.0))
                .andExpect(jsonPath("$.ratingCount").value(1));
        mvc.perform(get("/api/v1/review/games/" + game + "/statistics").with(actor(reader))).andExpect(jsonPath("$.ratings.ratingCount").value(1))
                .andExpect(jsonPath("$.recommendations.recommendedCount").value(1)).andExpect(jsonPath("$.reviews.activeCount").value(0));
    }

    @Test void theAuthorCannotEditAModeratedReviewButMayDeleteAHiddenOneNotAnArchivedOne() throws Exception {
        moderate(review, "HIDE", 0).andExpect(status().isOk());
        mvc.perform(patch("/api/v1/review/reviews/" + review).with(actor(author)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"version\":1,\"body\":\"Yeni\"}")).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("REVIEW_MODERATED"));
        // Gizlenen inceleme silinebilir; ikinci inceleme de yazılabilir hâle gelir.
        mvc.perform(delete("/api/v1/review/reviews/" + review).param("version", "1").with(actor(author))).andExpect(status().isNoContent());
        assertEquals("DELETED", statusOf(review));
        UUID archived = seed(author, UUID.randomUUID(), "2026-02-01T00:00:00Z");
        moderate(archived, "ARCHIVE", 0).andExpect(status().isOk());
        mvc.perform(delete("/api/v1/review/reviews/" + archived).param("version", "1").with(actor(author))).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("REVIEW_MODERATED"));
        mvc.perform(patch("/api/v1/review/reviews/" + archived).with(actor(author)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"version\":1,\"body\":\"Yeni\"}")).andExpect(status().isForbidden());
        assertEquals("ARCHIVED", statusOf(archived));
    }

    @Test void aModeratedReviewStillHoldsTheAuthorsOnlyActiveReviewSlot() throws Exception {
        moderate(review, "HIDE", 0).andExpect(status().isOk());
        org.mockito.Mockito.doNothing().when(games).requireAvailable(game);
        mvc.perform(post("/api/v1/review/games/" + game + "/reviews").with(actor(author)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"body\":\"İkinci\"}")).andExpect(status().isConflict());
    }

    @Test void onlyValidTransitionsAreAllowed() throws Exception {
        moderate(review, "RESTORE", 0).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("INVALID_MODERATION_TRANSITION"));
        moderate(review, "HIDE", 0).andExpect(status().isOk());
        moderate(review, "HIDE", 1).andExpect(status().isConflict());
        moderate(review, "ARCHIVE", 1).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ARCHIVED"));
        moderate(review, "RESTORE", 2).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("INVALID_MODERATION_TRANSITION"));
        moderate(review, "HIDE", 2).andExpect(status().isConflict());
        moderate(review, "ARCHIVE", 2).andExpect(status().isConflict());
        assertEquals("ARCHIVED", statusOf(review));
        assertEquals(2L, jdbc.sql("SELECT count(*) FROM review_moderation_actions WHERE review_id = ?").param(review).query(Long.class).single());
    }

    @Test void aStaleVersionIsRejectedWithoutChangingAnything() throws Exception {
        mvc.perform(patch("/api/v1/review/reviews/" + review).with(actor(author)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"version\":0,\"body\":\"Düzenlendi\"}")).andExpect(status().isOk());
        moderate(review, "HIDE", 0).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("REVIEW_VERSION_CONFLICT"));
        assertEquals("VISIBLE", statusOf(review));
        moderate(review, "HIDE", versionOf(review)).andExpect(status().isOk());
    }

    @Test void invalidModerationRequestsAreRejected() throws Exception {
        for (String body : List.of("{}", "{\"action\":\"HIDE\",\"version\":0}", "{\"action\":\"HIDE\",\"reason\":\"  \",\"version\":0}",
                "{\"action\":\"HIDE\",\"reason\":\"" + "a".repeat(501) + "\",\"version\":0}", "{\"action\":\"DELETE\",\"reason\":\"x\",\"version\":0}",
                "{\"action\":\"HIDE\",\"reason\":\"x\"}", "{\"action\":\"HIDE\",\"reason\":\"x\",\"version\":-1}",
                "{\"action\":\"HIDE\",\"reason\":\"kötü\\u0000\",\"version\":0}", "{\"action\":\"HIDE\",\"reason\":\"x\",\"version\":0,\"actorId\":\"" + author + "\"}")) {
            mvc.perform(post(ADMIN + "/reviews/" + review + "/moderation").with(as(moderator, "MODERATOR")).contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        }
        assertEquals("VISIBLE", statusOf(review));
        moderate(UUID.randomUUID(), "HIDE", 0).andExpect(status().isNotFound());
        mvc.perform(post(ADMIN + "/reviews/not-a-uuid/moderation").with(as(moderator, "MODERATOR")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"action\":\"HIDE\",\"reason\":\"x\",\"version\":0}")).andExpect(status().isBadRequest());
    }

    @Test void aDeletedReviewCannotBeModeratedAndHasNoBodyInTheAdminView() throws Exception {
        mvc.perform(delete("/api/v1/review/reviews/" + review).param("version", "0").with(actor(author))).andExpect(status().isNoContent());
        moderate(review, "HIDE", 1).andExpect(status().isNotFound());
        mvc.perform(get(ADMIN + "/reviews/" + review).with(as(moderator, "MODERATOR"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DELETED")).andExpect(jsonPath("$.body").doesNotExist());
        mvc.perform(get(ADMIN + "/reviews/" + UUID.randomUUID()).with(as(moderator, "MODERATOR"))).andExpect(status().isNotFound());
    }

    @Test void moderationUpdatesTheAuthorsSummaryOutbox() throws Exception {
        jdbc.sql("DELETE FROM review_user_summary_outbox").update();
        moderate(review, "HIDE", 0).andExpect(status().isOk());
        assertEquals(1L, jdbc.sql("SELECT count(*) FROM review_user_summary_outbox WHERE user_id = ?").param(author).query(Long.class).single());
        assertEquals(0, jdbc.sql("SELECT count(*) FROM reviews WHERE user_id = ? AND status = 'VISIBLE'").param(author).query(Integer.class).single());
    }

    @Test void reportedReviewsAreGroupedOrderedPagedAndNeverExposeIdentities() throws Exception {
        UUID second = seed(author, UUID.randomUUID(), "2026-01-02T00:00:00Z");
        UUID third = seed(author, UUID.randomUUID(), "2026-01-03T00:00:00Z");
        reportBy(UUID.randomUUID(), review, "SPAM", "2026-03-01T00:00:00Z");
        reportBy(UUID.randomUUID(), review, "OTHER", "2026-03-02T00:00:00Z");
        reportBy(UUID.randomUUID(), second, "SPAM", "2026-03-05T00:00:00Z");
        mvc.perform(get(ADMIN + "/reports").with(as(moderator, "MODERATOR"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2)).andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.items[0].reviewId").value(review.toString())).andExpect(jsonPath("$.items[0].reportCount").value(2))
                .andExpect(jsonPath("$.items[0].totalReportCount").value(2)).andExpect(jsonPath("$.items[0].body").value("Deneyim"))
                .andExpect(jsonPath("$.items[1].reviewId").value(second.toString())).andExpect(jsonPath("$.items[0].reporterId").doesNotExist())
                .andExpect(jsonPath("$.items[0].userId").doesNotExist());
        mvc.perform(get(ADMIN + "/reports").param("size", "1").param("page", "1").with(as(moderator, "MODERATOR")))
                .andExpect(jsonPath("$.items.length()").value(1)).andExpect(jsonPath("$.items[0].reviewId").value(second.toString()));
        mvc.perform(get(ADMIN + "/reports").param("page", "9").with(as(moderator, "MODERATOR"))).andExpect(jsonPath("$.items").isEmpty());
        mvc.perform(get(ADMIN + "/reports").param("status", "DISMISSED").with(as(moderator, "MODERATOR"))).andExpect(jsonPath("$.totalElements").value(0));
        for (String[] invalid : new String[][] {{"status", "OPEN"}, {"page", "-1"}, {"size", "0"}, {"size", "51"}}) {
            mvc.perform(get(ADMIN + "/reports").param(invalid[0], invalid[1]).with(as(moderator, "MODERATOR"))).andExpect(status().isBadRequest());
        }
        mvc.perform(get(ADMIN + "/reviews/" + review + "/reports").with(as(moderator, "MODERATOR"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2)).andExpect(jsonPath("$[0].reason").value("OTHER")).andExpect(jsonPath("$[0].status").value("RECEIVED"))
                .andExpect(jsonPath("$[0].reporterId").doesNotExist());
        mvc.perform(get(ADMIN + "/reviews/" + third + "/reports").with(as(moderator, "MODERATOR"))).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get(ADMIN + "/reviews/" + UUID.randomUUID() + "/reports").with(as(moderator, "MODERATOR"))).andExpect(status().isNotFound());
    }

    @Test void closingReportsRecordsTheOutcomeOnceAndHidesItFromTheReporter() throws Exception {
        UUID reporter = UUID.randomUUID();
        reportBy(reporter, review, "SPAM", "2026-03-01T00:00:00Z");
        reportBy(UUID.randomUUID(), review, "OTHER", "2026-03-02T00:00:00Z");
        mvc.perform(post(ADMIN + "/reviews/" + review + "/reports/close").with(as(moderator, "MODERATOR")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"outcome\":\"DISMISSED\",\"reason\":\"Kural ihlali yok\"}")).andExpect(status().isOk()).andExpect(jsonPath("$.closedCount").value(2));
        mvc.perform(post(ADMIN + "/reviews/" + review + "/reports/close").with(as(moderator, "MODERATOR")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"outcome\":\"REVIEWED\",\"reason\":\"Tekrar\"}")).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("REPORT_NOT_OPEN"));
        mvc.perform(get(ADMIN + "/reports").with(as(moderator, "MODERATOR"))).andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get(ADMIN + "/reports").param("status", "DISMISSED").with(as(moderator, "MODERATOR")))
                .andExpect(jsonPath("$.items[0].reportCount").value(2)).andExpect(jsonPath("$.items[0].totalReportCount").value(2));
        mvc.perform(get(ADMIN + "/reviews/" + review + "/reports").with(as(moderator, "MODERATOR"))).andExpect(jsonPath("$[0].status").value("DISMISSED"))
                .andExpect(jsonPath("$[0].closedAt").isNotEmpty());
        // Raportör yalnız "kapandı" görür; sonucu (reddedildi/işlem yapıldı) öğrenmez.
        mvc.perform(get("/api/v1/review/reviews/" + review + "/reports/mine").with(actor(reporter))).andExpect(jsonPath("$.status").value("CLOSED"));
        mvc.perform(get(ADMIN + "/reviews/" + review).with(as(moderator, "MODERATOR"))).andExpect(jsonPath("$.history[0].action").value("REPORTS_DISMISSED"))
                .andExpect(jsonPath("$.history[0].reason").value("Kural ihlali yok"));
        assertEquals(2L, jdbc.sql("SELECT count(*) FROM review_reports WHERE review_id = ? AND closed_by IS NOT NULL").param(review).query(Long.class).single());
    }

    @Test void closingRequiresAReasonAnOutcomeAndAnExistingReview() throws Exception {
        reportBy(UUID.randomUUID(), review, "SPAM", "2026-03-01T00:00:00Z");
        for (String body : List.of("{}", "{\"outcome\":\"REVIEWED\"}", "{\"outcome\":\"REVIEWED\",\"reason\":\" \"}", "{\"outcome\":\"RECEIVED\",\"reason\":\"x\"}")) {
            mvc.perform(post(ADMIN + "/reviews/" + review + "/reports/close").with(as(moderator, "MODERATOR")).contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        }
        mvc.perform(post(ADMIN + "/reviews/" + UUID.randomUUID() + "/reports/close").with(as(moderator, "MODERATOR")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"outcome\":\"REVIEWED\",\"reason\":\"x\"}")).andExpect(status().isNotFound());
        assertEquals(1L, jdbc.sql("SELECT count(*) FROM review_reports WHERE review_id = ? AND status = 'RECEIVED'").param(review).query(Long.class).single());
    }

    @Test void reportsOfAModeratedReviewRemainAvailableToModerators() throws Exception {
        reportBy(UUID.randomUUID(), review, "SPAM", "2026-03-01T00:00:00Z");
        moderate(review, "HIDE", 0).andExpect(status().isOk());
        mvc.perform(get(ADMIN + "/reports").with(as(moderator, "MODERATOR"))).andExpect(jsonPath("$.items[0].reviewStatus").value("HIDDEN"))
                .andExpect(jsonPath("$.items[0].body").value("Deneyim"));
    }

    @Test void erasingAModeratorAnonymizesTheirAuditTrailButKeepsTheRecord() throws Exception {
        reportBy(UUID.randomUUID(), review, "SPAM", "2026-03-01T00:00:00Z");
        moderate(review, "HIDE", 0).andExpect(status().isOk());
        mvc.perform(post(ADMIN + "/reviews/" + review + "/reports/close").with(as(moderator, "MODERATOR")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"outcome\":\"REVIEWED\",\"reason\":\"Gizlendi\"}")).andExpect(status().isOk());
        erasure.erase(moderator);
        assertEquals(1L, jdbc.sql("SELECT count(*) FROM review_moderation_actions WHERE review_id = ? AND action = 'HIDE' AND actor_id IS NULL").param(review).query(Long.class).single());
        assertEquals(0L, jdbc.sql("SELECT count(*) FROM review_reports WHERE closed_by IS NOT NULL AND review_id = ?").param(review).query(Long.class).single());
        assertEquals(2L, jdbc.sql("SELECT count(*) FROM review_moderation_actions WHERE review_id = ?").param(review).query(Long.class).single());
        // Silinen moderatör hesabının eski token'ı karar veremez.
        moderate(review, "RESTORE", versionOf(review)).andExpect(status().isForbidden());
        assertEquals("HIDDEN", statusOf(review));
    }

    @Test void erasingTheAuthorRemovesTheModerationRecordsWithTheirReview() throws Exception {
        moderate(review, "HIDE", 0).andExpect(status().isOk());
        erasure.erase(author);
        assertEquals(0L, jdbc.sql("SELECT count(*) FROM review_moderation_actions WHERE review_id = ?").param(review).query(Long.class).single());
    }
}
