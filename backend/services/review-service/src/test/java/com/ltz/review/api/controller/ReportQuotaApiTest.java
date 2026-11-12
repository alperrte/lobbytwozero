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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import static com.ltz.review.api.controller.RatingApiTest.actor;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** REV-13 rapor kotası: her deneme (mükerrer, kendi incelemesi, geçersiz dahil) sayılır; aşımda 429 + Retry-After. */
@SpringBootTest(properties = {"ltz.review.abuse.report-create.limit=3", "ltz.review.abuse.report-create.window=3600s"})
class ReportQuotaApiTest {
    @Autowired WebApplicationContext context;
    @Autowired JdbcClient jdbc;
    @MockitoBean GameCatalogPort games;
    MockMvc mvc;
    UUID reporter;
    UUID author;

    @BeforeEach void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(context).defaultRequest(get("/").with(csrf())).apply(springSecurity()).build();
        reporter = UUID.randomUUID();
        author = UUID.randomUUID();
    }

    UUID seed(UUID owner) {
        UUID id = UUID.randomUUID();
        var time = OffsetDateTime.parse("2026-01-01T00:00:00Z");
        jdbc.sql("INSERT INTO reviews (id,user_id,game_id,body,status,created_at,updated_at) VALUES (?,?,?,?,?,?,?)")
                .params(id, owner, UUID.randomUUID(), "Deneyim", "VISIBLE", time, time).update();
        return id;
    }

    ResultActions report(UUID who, UUID target, String json) throws Exception {
        return mvc.perform(post("/api/v1/review/reviews/" + target + "/reports").with(actor(who)).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    @Test void everyAttemptCountsAndTheFourthIsLimitedWithoutBusinessEffects() throws Exception {
        UUID target = seed(author);
        UUID own = seed(reporter);
        report(reporter, target, "{\"reason\":\"SPAM\"}").andExpect(status().isCreated());
        report(reporter, target, "{\"reason\":\"SPAM\"}").andExpect(status().isConflict());
        report(reporter, own, "{\"reason\":\"SPAM\"}").andExpect(status().isForbidden());
        UUID another = seed(author);
        report(reporter, another, "{\"reason\":\"SPAM\"}").andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After")).andExpect(jsonPath("$.status").value(429));
        assertEquals(0, jdbc.sql("SELECT count(*) FROM review_reports WHERE review_id = ?").param(another).query(Long.class).single());
        // Başka kullanıcı etkilenmez; okuma kotaya girmez.
        report(UUID.randomUUID(), another, "{\"reason\":\"SPAM\"}").andExpect(status().isCreated());
        mvc.perform(get("/api/v1/review/reviews/" + target + "/reports/mine").with(actor(reporter))).andExpect(status().isOk());
    }

    @Test void invalidBodiesStillConsumeTheQuota() throws Exception {
        UUID target = seed(author);
        for (int i = 0; i < 3; i++) report(reporter, target, "{}").andExpect(status().isBadRequest());
        report(reporter, target, "{\"reason\":\"SPAM\"}").andExpect(status().isTooManyRequests());
    }
}
