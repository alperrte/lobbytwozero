package com.ltz.hardware.api.controller;

import com.ltz.hardware.application.port.AuthenticatedActor;
import com.ltz.hardware.infrastructure.persistence.PostgresIntegrationSupport;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"logging.level.root=INFO", "debug=false"})
@AutoConfigureMockMvc
class HardwareProfileControllerTest extends PostgresIntegrationSupport {
    private static final String BASE = "/api/v1/hardware/profiles";
    private static final UUID OWNER = UUID.randomUUID();
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void clearProfiles() {
        jdbc.update("DELETE FROM hardware_profiles");
    }

    static RequestPostProcessor actor(UUID userId) {
        return authentication(new UsernamePasswordAuthenticationToken(
                new AuthenticatedActor(userId), null, List.of()));
    }

    @Test
    void createsProfileWithVerifiedOwnerAndDoesNotExposeOwner() throws Exception {
        mvc.perform(post(BASE).with(actor(OWNER)).with(csrf()).contentType("application/json")
                        .content("{\"name\":\"  Oyun PC  \",\"deviceType\":\"DESKTOP\"}"))
                .andExpect(status().isCreated()).andExpect(header().exists("Location"))
                .andExpect(header().string("Cache-Control", "no-store, private"))
                .andExpect(jsonPath("$.name").value("Oyun PC"))
                .andExpect(jsonPath("$.source").value("MANUAL"))
                .andExpect(jsonPath("$.version").value(0))
                .andExpect(jsonPath("$.userId").doesNotExist());
        assertThat(jdbc.queryForObject("SELECT user_id FROM hardware_profiles", UUID.class)).isEqualTo(OWNER);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"name\":\" \",\"deviceType\":\"DESKTOP\"}",
            "{\"name\":\"PC\",\"deviceType\":\"INVALID\"}",
            "{\"name\":\"PC\"}",
            "{\"name\":\"<script>\",\"deviceType\":\"DESKTOP\"}",
            "{\"name\":\"PC\",\"deviceType\":\"DESKTOP\",\"userId\":\"00000000-0000-0000-0000-000000000001\"}",
            "{\"name\":\"PC\",\"deviceType\":\"DESKTOP\",\"source\":\"DESKTOP_SYNC\"}"})
    void rejectsInvalidAndMassAssignmentBodies(String body) throws Exception {
        mvc.perform(post(BASE).with(actor(OWNER)).with(csrf()).contentType("application/json").content(body))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM hardware_profiles", Long.class)).isZero();
    }

    @Test
    void rejectsAnonymousForgedHeadersBasicBearerAndCookie() throws Exception {
        mvc.perform(post(BASE).with(csrf()).header("X-User-Id", OWNER).header("X-Role", "ADMIN")
                        .header("Authorization", "Bearer invalid").header("Cookie", "JSESSIONID=fake")
                        .contentType("application/json").content("{\"name\":\"PC\",\"deviceType\":\"DESKTOP\"}"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
        mvc.perform(post(BASE).with(httpBasic("test", "test")).with(csrf()))
                .andExpect(status().isUnauthorized());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM hardware_profiles", Long.class)).isZero();
    }

    @Test
    void rejectsMissingCsrfAndUntrustedPrincipal() throws Exception {
        mvc.perform(post(BASE).with(actor(OWNER)).contentType("application/json")
                        .content("{\"name\":\"PC\",\"deviceType\":\"DESKTOP\"}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        mvc.perform(post(BASE).with(user("test").roles("ADMIN")).with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    void updatesOnlyOwnedProfileWithMatchingVersion() throws Exception {
        mvc.perform(post(BASE).with(actor(OWNER)).with(csrf()).contentType("application/json")
                .content("{\"name\":\"PC\",\"deviceType\":\"DESKTOP\"}")).andExpect(status().isCreated());
        var id = jdbc.queryForObject("SELECT id FROM hardware_profiles", UUID.class);
        var body = "{\"profileVersion\":0,\"name\":\"Laptop\",\"deviceType\":\"LAPTOP\"}";
        mvc.perform(put(BASE + "/" + id).with(actor(UUID.randomUUID())).with(csrf())
                .contentType("application/json").content(body)).andExpect(status().isNotFound());
        mvc.perform(put(BASE + "/" + id).with(actor(OWNER)).with(csrf()).contentType("application/json").content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Laptop"))
                .andExpect(jsonPath("$.version").value(1));
        mvc.perform(put(BASE + "/" + id).with(actor(OWNER)).with(csrf()).contentType("application/json").content(body))
                .andExpect(status().isConflict());
    }

    @Test
    void listsMultipleIndependentComputersWithStableOwnerScopedPagination() throws Exception {
        for (var type : new String[]{"DESKTOP", "LAPTOP", "OTHER"}) {
            mvc.perform(post(BASE).with(actor(OWNER)).with(csrf()).contentType("application/json")
                    .content("{\"name\":\"" + type + "\",\"deviceType\":\"" + type + "\"}"))
                    .andExpect(status().isCreated());
        }
        mvc.perform(post(BASE).with(actor(UUID.randomUUID())).with(csrf()).contentType("application/json")
                .content("{\"name\":\"Secret PC\",\"deviceType\":\"DESKTOP\"}")).andExpect(status().isCreated());
        jdbc.update("UPDATE hardware_profiles SET created_at = '2026-01-01T00:00:00Z'");
        var expected = jdbc.queryForList("SELECT id FROM hardware_profiles WHERE user_id=? ORDER BY created_at,id", UUID.class, OWNER);
        mvc.perform(get(BASE).with(actor(OWNER)).param("size", "2"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2)).andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].id").value(expected.get(0).toString()))
                .andExpect(jsonPath("$.items[1].id").value(expected.get(1).toString()));
        mvc.perform(get(BASE).with(actor(OWNER)).param("size", "2").param("page", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].id").value(expected.get(2).toString()));
        mvc.perform(get(BASE).with(actor(UUID.randomUUID())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get(BASE)).andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @ValueSource(strings = {"-1", "0", "101", "2147483647"})
    void rejectsUnboundedPageSizes(String size) throws Exception {
        mvc.perform(get(BASE).with(actor(OWNER)).param("size", size)).andExpect(status().isBadRequest());
    }

    @Test
    void rejectsOversizedBodyEvenWithoutContentLength() throws Exception {
        mvc.perform(post(BASE).with(actor(OWNER)).with(csrf()).contentType("application/json")
                        .content("{\"name\":\"" + "x".repeat(17000) + "\",\"deviceType\":\"DESKTOP\"}")
                        .with(request -> { request.removeHeader("Content-Length"); return request; }))
                .andExpect(status().is(413)).andExpect(jsonPath("$.code").value("REQUEST_TOO_LARGE"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM hardware_profiles", Long.class)).isZero();
    }
}
