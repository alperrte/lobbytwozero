package com.ltz.hardware.api.controller;

import com.ltz.hardware.infrastructure.persistence.PostgresIntegrationSupport;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;
import static com.ltz.hardware.api.controller.HardwareProfileControllerTest.actor;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"logging.level.root=INFO", "debug=false"})
@AutoConfigureMockMvc
class HardwareBatchControllerTest extends PostgresIntegrationSupport {
    private static final String BASE = "/api/v1/hardware/profiles/batch";
    private static final UUID OWNER = UUID.randomUUID();
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired jakarta.persistence.EntityManagerFactory entityManagerFactory;
    UUID a;
    UUID b;
    UUID other;

    @BeforeEach
    void prepare() {
        jdbc.update("DELETE FROM hardware_profiles");
        a = profile(OWNER);
        b = profile(OWNER);
        other = profile(UUID.randomUUID());
        for (int i = 0; i < 5; i++) component(a, "A" + i);
        component(other, "Secret");
    }
    private UUID profile(UUID owner) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO hardware_profiles(id,user_id,name,device_type,created_at,updated_at) VALUES (?,?,?,'DESKTOP',now(),now())",
                id, owner, "Personal name");
        return id;
    }
    private void component(UUID id, String model) {
        jdbc.update("INSERT INTO hardware_profile_components(id,profile_id,category,model_name,quantity,capacity_gb,created_at,updated_at)"
                + " VALUES (?,?,'RAM',?,2,16,'2026-01-01T00:00:00Z','2026-01-01T00:00:00Z')", UUID.randomUUID(), id, model);
    }
    private Map<String, Object> page(UUID id, int number, int size) {
        return Map.of("profileId", id, "page", number, "size", size);
    }
    private String body(List<Map<String, Object>> pages) { return json.writeValueAsString(Map.of("profiles", pages)); }

    @Test
    void returnsStableIndependentPagesAndExplicitContinuationWithoutPersonalData() throws Exception {
        var ordered = jdbc.queryForList("SELECT id FROM hardware_profile_components WHERE profile_id=? ORDER BY created_at,id", UUID.class, a);
        var response = mvc.perform(post(BASE).with(actor(OWNER)).with(csrf()).contentType("application/json")
                .content(body(List.of(page(b, 0, 2), page(a, 1, 2)))))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store, private"))
                .andExpect(jsonPath("$.profiles.length()").value(2))
                .andExpect(jsonPath("$.profiles[0].profileId").value(b.toString()))
                .andExpect(jsonPath("$.profiles[0].components").isEmpty())
                .andExpect(jsonPath("$.profiles[0].page.totalElements").value(0))
                .andExpect(jsonPath("$.profiles[0].page.totalPages").value(0))
                .andExpect(jsonPath("$.profiles[0].page.hasNext").value(false))
                .andExpect(jsonPath("$.profiles[0].page.nextPage").isEmpty())
                .andExpect(jsonPath("$.profiles[1].components[0].id").value(ordered.get(2).toString()))
                .andExpect(jsonPath("$.profiles[1].components[1].id").value(ordered.get(3).toString()))
                .andExpect(jsonPath("$.profiles[1].components[0].specifications.capacityGb").value(16))
                .andExpect(jsonPath("$.profiles[1].page.totalElements").value(5))
                .andExpect(jsonPath("$.profiles[1].page.totalPages").value(3))
                .andExpect(jsonPath("$.profiles[1].page.nextPage").value(2))
                .andExpect(jsonPath("$.profiles[1].page.hasNext").value(true))
                .andExpect(jsonPath("$.profiles[1].name").doesNotExist())
                .andExpect(jsonPath("$.profiles[1].userId").doesNotExist())
                .andExpect(jsonPath("$.profiles[1].visibility").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        assertThat(response).doesNotContain("Personal name", "serialNumber", "ownerId", "Secret");
        mvc.perform(post(BASE).with(actor(OWNER)).with(csrf()).contentType("application/json")
                .content(body(List.of(page(a, 2, 2)))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.profiles[0].components.length()").value(1))
                .andExpect(jsonPath("$.profiles[0].page.hasNext").value(false));
        mvc.perform(post(BASE).with(actor(OWNER)).with(csrf()).contentType("application/json")
                .content(body(List.of(page(a, 10000, 100)))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.profiles[0].components").isEmpty())
                .andExpect(jsonPath("$.profiles[0].page.totalElements").value(5));
    }

    @Test
    void anyMissingOrOtherOwnerResourceRejectsTheEntireBatchEvenWhenPublic() throws Exception {
        jdbc.update("UPDATE hardware_profiles SET visibility='PUBLIC' WHERE id=?", other);
        for (var forbidden : List.of(other, UUID.randomUUID())) {
            mvc.perform(post(BASE).with(actor(OWNER)).with(csrf()).contentType("application/json")
                    .content(body(List.of(page(a, 0, 2), page(forbidden, 0, 2)))))
                    .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("HARDWARE_NOT_FOUND"))
                    .andExpect(jsonPath("$.profiles").doesNotExist());
        }
    }

    @Test
    void enforcesUniqueProfilesAndTotalRequestedRowsNotJustReturnedRows() throws Exception {
        mvc.perform(post(BASE).with(actor(OWNER)).with(csrf()).contentType("application/json")
                .content(body(List.of(page(a, 0, 1), page(a, 1, 1)))))
                .andExpect(status().isBadRequest());
        var third = profile(OWNER);
        mvc.perform(post(BASE).with(actor(OWNER)).with(csrf()).contentType("application/json")
                .content(body(List.of(page(a, 0, 100), page(b, 0, 100), page(third, 0, 1)))))
                .andExpect(status().isBadRequest());
        mvc.perform(post(BASE).with(actor(OWNER)).with(csrf()).contentType("application/json")
                .content(body(List.of(page(a, 0, 100), page(b, 0, 100)))))
                .andExpect(status().isOk());
    }

    @Test
    void acceptsTwentyProfilesButRejectsTwentyOne() throws Exception {
        var pages = new ArrayList<Map<String, Object>>();
        for (int i = 0; i < 20; i++) {
            var profileId = profile(OWNER);
            component(profileId, "Batch " + i);
            pages.add(page(profileId, 0, 10));
        }
        var statistics = entityManagerFactory.unwrap(org.hibernate.SessionFactory.class).getStatistics();
        statistics.setStatisticsEnabled(true);
        statistics.clear();
        try {
            mvc.perform(post(BASE).with(actor(OWNER)).with(csrf()).contentType("application/json").content(body(pages)))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.profiles.length()").value(20));
            // Three ORM statements, plus one JDBC LATERAL query: independent of profile count.
            assertThat(statistics.getPrepareStatementCount()).isEqualTo(3);
        } finally {
            statistics.setStatisticsEnabled(false);
        }
        pages.add(page(profile(OWNER), 0, 1));
        mvc.perform(post(BASE).with(actor(OWNER)).with(csrf()).contentType("application/json").content(body(pages)))
                .andExpect(status().isBadRequest());
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"profiles\":[]}", "{\"profiles\":[null]}", "{\"profiles\":[{}]}",
        "{\"profiles\":[],\"userId\":\"fake\"}"})
    void validatesNestedInputAndRejectsMassAssignment(String body) throws Exception {
        mvc.perform(post(BASE).with(actor(OWNER)).with(csrf()).contentType("application/json").content(body))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"-1:1", "10001:1", "0:0", "0:101"})
    void boundsEachRequestedPage(String values) throws Exception {
        var parts = values.split(":");
        mvc.perform(post(BASE).with(actor(OWNER)).with(csrf()).contentType("application/json")
                .content(body(List.of(page(a, Integer.parseInt(parts[0]), Integer.parseInt(parts[1]))))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void requiresTrustedActorCsrfAndBodyLimit() throws Exception {
        var body = body(List.of(page(a, 0, 1)));
        mvc.perform(post(BASE).with(csrf()).header("X-User-Id", OWNER).header("X-Role", "ADMIN")
                .contentType("application/json").content(body)).andExpect(status().isUnauthorized());
        mvc.perform(post(BASE).with(actor(OWNER)).contentType("application/json").content(body))
                .andExpect(status().isForbidden());
        mvc.perform(post(BASE).with(user("test").roles("ADMIN")).with(csrf()).contentType("application/json").content(body))
                .andExpect(status().isForbidden());
        mvc.perform(post(BASE).with(actor(OWNER)).with(csrf()).contentType("application/json").content(" ".repeat(17000)))
                .andExpect(status().is(413));
    }
}
