package com.ltz.game.api.controller;

import com.ltz.game.AuthenticatedApiTest;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Herkese açık toplu kart ucunun kötüye kullanıma karşı korunması: istek sınırı (429) ve CSRF istisnasının kapsamı.
 * Her test kendine ait bağlantı adresini kullanır; sınır durumu uygulama bağlamında paylaşıldığı için adresler çakışmaz.
 */
@Import(AuthenticatedApiTest.Fakes.class)
class BatchProtectionTest extends AuthenticatedApiTest {
    static final String PATH = "/api/v1/game/games/batch";

    static MockHttpServletRequestBuilder from(MockHttpServletRequestBuilder builder, String remote) {
        return builder.with(request -> {
            request.setRemoteAddr(remote);
            return request;
        });
    }

    String body() { return "{\"gameIds\":[\"" + UUID.randomUUID() + "\"]}"; }

    MockHttpServletRequestBuilder batchFrom(String remote) {
        return from(post(PATH).contentType(MediaType.APPLICATION_JSON).content(body()), remote);
    }

    // ------------------------------------------------------------------ rate limit

    @Test void exceedingTheBurstReturns429WithRetryAfterAndProblemJsonWhileOtherClientsAndRoutesStayAvailable() throws Exception {
        for (int i = 0; i < 100; i++) mvc.perform(batchFrom("198.51.100.20")).andExpect(status().isOk());
        mvc.perform(batchFrom("198.51.100.20")).andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.status").value(429)).andExpect(jsonPath("$.code").value("RATE_LIMITED"));
        long retryAfter = Long.parseLong(mvc.perform(batchFrom("198.51.100.20")).andReturn().getResponse().getHeader("Retry-After"));
        assertTrue(retryAfter >= 1, "Retry-After: " + retryAfter);
        // Başka istemci, aynı istemcinin diğer uçları ve korumalı uçların kimlik davranışı etkilenmez.
        mvc.perform(batchFrom("198.51.100.21")).andExpect(status().isOk());
        mvc.perform(from(get("/api/v1/game/games"), "198.51.100.20")).andExpect(status().isOk());
        mvc.perform(from(get("/api/v1/game/" + UUID.randomUUID() + "/card"), "198.51.100.20")).andExpect(status().isNotFound());
        mvc.perform(from(get("/api/v1/game/admin/games/" + UUID.randomUUID()), "198.51.100.20")).andExpect(status().isUnauthorized());
        mvc.perform(from(get(PATH), "198.51.100.20")).andExpect(status().is4xxClientError()).andExpect(status().is(org.hamcrest.Matchers.not(429)));
    }

    @Test void normalClientTrafficBelowTheLimitIsNeverRejected() throws Exception {
        for (int i = 0; i < 60; i++) mvc.perform(batchFrom("198.51.100.22")).andExpect(status().isOk());
    }

    @Test void forgedForwardedForHeadersDoNotBypassTheLimitWhenNoProxyIsTrusted() throws Exception {
        int rejected = 0;
        for (int i = 0; i < 160; i++) {
            var request = batchFrom("198.51.100.30").header("X-Forwarded-For", "203.0.113." + (i % 250 + 1)).header("X-Real-IP", "192.0.2." + (i % 250 + 1));
            if (mvc.perform(request).andReturn().getResponse().getStatus() == 429) rejected++;
        }
        assertEquals(60, rejected, "ilk 100 istek geçer, kalan 60 istek sahte başlıklara rağmen reddedilir");
    }

    @Test void invalidRequestsConsumeTokensToo() throws Exception {
        for (int i = 0; i < 100; i++)
            mvc.perform(from(post(PATH).contentType(MediaType.APPLICATION_JSON).content("{}"), "198.51.100.40")).andExpect(status().isBadRequest());
        mvc.perform(batchFrom("198.51.100.40")).andExpect(status().isTooManyRequests());
    }

    @Test void onlyThePublicBatchPostIsRateLimited() throws Exception {
        for (int i = 0; i < 150; i++) {
            mvc.perform(from(get("/api/v1/game/games"), "198.51.100.50")).andExpect(status().isOk());
            mvc.perform(from(get(PATH), "198.51.100.50")).andExpect(status().is(org.hamcrest.Matchers.not(429)));
        }
        mvc.perform(batchFrom("198.51.100.50")).andExpect(status().isOk());
    }

    // ------------------------------------------------------------------ CSRF istisnasının kapsamı

    @Test void csrfExemptionRequiresAJsonContentType() throws Exception {
        for (MediaType type : List.of(MediaType.TEXT_PLAIN, MediaType.APPLICATION_FORM_URLENCODED, MediaType.MULTIPART_FORM_DATA,
                MediaType.parseMediaType("application/x-json"), MediaType.parseMediaType("application/jsonp"), MediaType.TEXT_HTML))
            mvc.perform(from(post(PATH).contentType(type).content(body()), "198.51.100.60")).andExpect(status().isForbidden());
        mvc.perform(from(post(PATH).content(body()), "198.51.100.60")).andExpect(status().isForbidden());
        for (String json : List.of("application/json", "application/json;charset=UTF-8", "APPLICATION/JSON"))
            mvc.perform(from(post(PATH).header("Content-Type", json).content(body()), "198.51.100.61")).andExpect(status().isOk());
        mvc.perform(from(post(PATH).header("Content-Type", "not a media type").content(body()), "198.51.100.62")).andExpect(status().isForbidden());
    }

    @Test void everyOtherMutatingRequestStillNeedsCsrfOrCredentialsEvenWithAJsonBody() throws Exception {
        UUID id = UUID.randomUUID();
        List<String> paths = List.of(
                PATH + "/", PATH + "x", PATH + "/extra", "/api/v1/game/games/Batch", "/API/V1/GAME/GAMES/BATCH",
                "/api/v1/game/games", "/api/v1/game/games/search", "/api/v1/game/games/popular", "/api/v1/game/games/" + id,
                "/api/v1/game/" + id + "/card", "/api/v1/game/" + id + "/metadata", "/api/v1/game/catalog", "/api/v1/game/catalog/status",
                "/api/v1/game/games/" + id + "/developer-content", "/api/v1/game/admin/games", "/api/v1/game/admin/popular",
                "/api/v1/game/admin/games/" + id, "/api/v1/game/admin/games/" + id + "/archive", "/api/v1/game/developer/games/" + id + "/description",
                "/api/v1/game/developer/games/" + id + "/media/" + id, "/api/v1/game/moderation/games/" + id + "/developer-content/actions",
                "/actuator/health", "/api/v1/game/unknown", "/something-else");
        for (String path : paths) {
            for (var request : List.of(post(path), put(path), patch(path), delete(path))) {
                int status = mvc.perform(from(request.contentType(MediaType.APPLICATION_JSON).content("{}"), "198.51.100.70")).andReturn().getResponse().getStatus();
                assertTrue(status == 401 || status == 403, path + " -> " + status);
            }
        }
    }

    @Test void methodOverrideParametersDoNotChangeWhatTheBatchEndpointDoes() throws Exception {
        mvc.perform(from(post(PATH + "?_method=DELETE").contentType(MediaType.APPLICATION_JSON).content(body()).header("X-HTTP-Method-Override", "DELETE"), "198.51.100.80"))
                .andExpect(status().isOk());
    }

    @Test void batchNeverChangesState() throws Exception {
        UUID active = game("Read only", "INTERNAL", "MAIN_GAME", "ACTIVE");
        UUID archived = game("Archived", "INTERNAL", "MAIN_GAME", "ARCHIVED");
        jdbc.update("insert into game_external_refs(id,game_id,provider,external_id) values (gen_random_uuid(),?,'STEAM','4242')", active);
        String before = snapshot();
        String ids = "{\"gameIds\":[\"" + active + "\",\"" + archived + "\",\"" + UUID.randomUUID() + "\"]}";
        for (int i = 0; i < 25; i++)
            mvc.perform(from(post(PATH).contentType(MediaType.APPLICATION_JSON).content(ids), "198.51.100.90")).andExpect(status().isOk());
        assertEquals(before, snapshot());
    }

    private String snapshot() {
        return jdbc.queryForObject("""
                select concat((select count(*) from games),'|',(select coalesce(sum(version),0) from games),'|',(select coalesce(max(updated_at)::text,'') from games),'|',
                (select count(*) from game_external_refs),'|',(select count(*) from game_media),'|',(select count(*) from game_admin_audits),'|',
                (select count(*) from game_field_overrides),'|',(select count(*) from game_developer_grants),'|',(select count(*) from game_developer_contents))
                """, String.class);
    }
}
