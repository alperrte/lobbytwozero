package com.ltz.hardware.api.controller;

import com.ltz.hardware.application.port.HardwareVisibilityPolicy;
import com.ltz.hardware.infrastructure.persistence.PostgresIntegrationSupport;
import com.ltz.hardware.infrastructure.security.UnavailableHardwareVisibilityPolicy;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static com.ltz.hardware.api.controller.HardwareProfileControllerTest.actor;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"logging.level.root=INFO", "debug=false"})
@AutoConfigureMockMvc
class HardwarePrivacyControllerTest extends PostgresIntegrationSupport {
    private static final String BASE = "/api/v1/hardware/profiles";
    private static final UUID OWNER = UUID.randomUUID();
    private static final UUID VIEWER = UUID.randomUUID();
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean HardwareVisibilityPolicy policy;
    UUID id;

    @BeforeEach
    void prepare() throws Exception {
        jdbc.update("DELETE FROM hardware_profiles");
        mvc.perform(post(BASE).with(actor(OWNER)).with(csrf()).contentType("application/json")
                .content("{\"name\":\"Secret PC\",\"deviceType\":\"DESKTOP\"}")).andExpect(status().isCreated());
        id = jdbc.queryForObject("SELECT id FROM hardware_profiles", UUID.class);
    }

    private String path(String suffix) { return BASE + "/" + id + suffix; }
    private String rules(String visibility, String fields, String categories, long version) {
        return "{\"profileVersion\":" + version + ",\"visibility\":\"" + visibility
                + "\",\"fields\":" + fields + ",\"categories\":" + categories + "}";
    }
    private void save(String visibility, String fields, String categories, long version) throws Exception {
        mvc.perform(put(path("/privacy")).with(actor(OWNER)).with(csrf()).contentType("application/json")
                .content(rules(visibility, fields, categories, version))).andExpect(status().isOk());
    }
    private void add(String category, int capacity) throws Exception {
        long version = jdbc.queryForObject("SELECT version FROM hardware_profiles WHERE id=?", Long.class, id);
        mvc.perform(post(path("/components")).with(actor(OWNER)).with(csrf()).contentType("application/json")
                .content("{\"profileVersion\":" + version + ",\"category\":\"" + category
                        + "\",\"modelName\":\"Secret model\",\"brandName\":\"Secret brand\",\"quantity\":2,"
                        + "\"specifications\":{\"capacityGb\":" + capacity + "}}"))
                .andExpect(status().isCreated());
    }

    @Test
    void defaultsPrivateAndOwnerPreviewBypassesExternalPolicy() throws Exception {
        mvc.perform(get(path("/privacy")).with(actor(OWNER))).andExpect(status().isOk())
                .andExpect(jsonPath("$.visibility").value("PRIVATE"))
                .andExpect(jsonPath("$.fields").isEmpty()).andExpect(jsonPath("$.categories").isEmpty());
        mvc.perform(get(path("/shared")).with(actor(VIEWER))).andExpect(status().isNotFound());
        mvc.perform(get(path("/shared")).with(actor(OWNER))).andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Secret PC"))
                .andExpect(jsonPath("$.deviceType").value("DESKTOP"));
        verify(policy, times(1)).evaluate(VIEWER, OWNER);
        assertThat(new UnavailableHardwareVisibilityPolicy().evaluate(VIEWER, OWNER).allowed()).isFalse();
    }

    @Test
    void privateProfileDeniesEvenWhenFieldsAndExternalPolicyAllow() throws Exception {
        save("PRIVATE", "{\"PROFILE_NAME\":\"PUBLIC\"}", "{}", 0);
        when(policy.evaluate(VIEWER, OWNER)).thenReturn(new HardwareVisibilityPolicy.Decision(true, true));
        mvc.perform(get(path("/shared")).with(actor(VIEWER))).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("HARDWARE_NOT_FOUND"));
    }

    @Test
    void filtersHiddenFieldsCategoriesIdsAndCountsBeforePagination() throws Exception {
        add("CPU", 8);
        add("RAM", 32);
        save("PUBLIC", "{\"PROFILE_NAME\":\"FRIENDS\",\"DEVICE_TYPE\":\"PUBLIC\","
                + "\"COMPONENT_QUANTITY\":\"PUBLIC\",\"TECHNICAL_SPECIFICATIONS\":\"FRIENDS\"}",
                "{\"RAM\":\"PUBLIC\",\"CPU\":\"PRIVATE\"}", 2);
        when(policy.evaluate(VIEWER, OWNER)).thenReturn(new HardwareVisibilityPolicy.Decision(true, false));
        mvc.perform(get(path("/shared")).with(actor(VIEWER)).param("size", "1"))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store, private"))
                .andExpect(jsonPath("$.name").doesNotExist()).andExpect(jsonPath("$.userId").doesNotExist())
                .andExpect(jsonPath("$.version").doesNotExist()).andExpect(jsonPath("$.deviceType").value("DESKTOP"))
                .andExpect(jsonPath("$.page.totalElements").value(1)).andExpect(jsonPath("$.page.totalPages").value(1))
                .andExpect(jsonPath("$.components[0].category").value("RAM"))
                .andExpect(jsonPath("$.components[0].quantity").value(2))
                .andExpect(jsonPath("$.components[0].id").doesNotExist())
                .andExpect(jsonPath("$.components[0].productId").doesNotExist())
                .andExpect(jsonPath("$.components[0].brandId").doesNotExist())
                .andExpect(jsonPath("$.components[0].modelName").doesNotExist())
                .andExpect(jsonPath("$.components[0].brandName").doesNotExist())
                .andExpect(jsonPath("$.components[0].specifications").doesNotExist());
        mvc.perform(get(path("/shared")).with(actor(VIEWER)).param("size", "1").param("page", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.components").isEmpty());
        when(policy.evaluate(VIEWER, OWNER)).thenReturn(new HardwareVisibilityPolicy.Decision(true, true));
        mvc.perform(get(path("/shared")).with(actor(VIEWER))).andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Secret PC"))
                .andExpect(jsonPath("$.components[0].specifications.capacityGb").value(32));
    }

    @Test
    void friendsGateAndEveryFieldMayBeExplicitlySharedWithoutMetadata() throws Exception {
        add("CPU", 8);
        save("FRIENDS", "{\"PROFILE_NAME\":\"PUBLIC\",\"COMPONENT_MODEL\":\"PUBLIC\","
                + "\"COMPONENT_BRAND\":\"PUBLIC\",\"COMPONENT_QUANTITY\":\"PUBLIC\","
                + "\"TECHNICAL_SPECIFICATIONS\":\"PUBLIC\"}", "{\"CPU\":\"FRIENDS\"}", 1);
        when(policy.evaluate(VIEWER, OWNER)).thenReturn(new HardwareVisibilityPolicy.Decision(true, false));
        mvc.perform(get(path("/shared")).with(actor(VIEWER))).andExpect(status().isNotFound());
        when(policy.evaluate(VIEWER, OWNER)).thenReturn(new HardwareVisibilityPolicy.Decision(true, true));
        mvc.perform(get(path("/shared")).with(actor(VIEWER))).andExpect(status().isOk())
                .andExpect(jsonPath("$.components[0].modelName").value("Secret model"))
                .andExpect(jsonPath("$.components[0].brandName").value("Secret brand"))
                .andExpect(jsonPath("$.components[0].specifications.capacityGb").value(8));
    }

    @Test
    void emptyRulesOmitAllComponentExistenceAndPutReplacesOldRules() throws Exception {
        add("CPU", 8);
        save("PUBLIC", "{\"COMPONENT_MODEL\":\"PUBLIC\"}", "{\"CPU\":\"PUBLIC\"}", 1);
        save("PUBLIC", "{}", "{\"CPU\":\"PUBLIC\"}", 2);
        when(policy.evaluate(VIEWER, OWNER)).thenReturn(new HardwareVisibilityPolicy.Decision(true, true));
        mvc.perform(get(path("/shared")).with(actor(VIEWER))).andExpect(status().isOk())
                .andExpect(jsonPath("$.components").isEmpty()).andExpect(jsonPath("$.page.totalElements").value(0));
        save("PUBLIC", "{\"COMPONENT_MODEL\":\"PUBLIC\"}", "{}", 3);
        mvc.perform(get(path("/shared")).with(actor(VIEWER))).andExpect(status().isOk())
                .andExpect(jsonPath("$.components").isEmpty()).andExpect(jsonPath("$.page.totalElements").value(0));
    }

    @Test
    void externalBlockedNullAndUnavailableDecisionsNeverExposeData() throws Exception {
        save("PUBLIC", "{\"PROFILE_NAME\":\"PUBLIC\"}", "{}", 0);
        when(policy.evaluate(VIEWER, OWNER)).thenReturn(new HardwareVisibilityPolicy.Decision(false, true));
        mvc.perform(get(path("/shared")).with(actor(VIEWER))).andExpect(status().isNotFound());
        when(policy.evaluate(VIEWER, OWNER)).thenReturn(null);
        mvc.perform(get(path("/shared")).with(actor(VIEWER))).andExpect(status().isNotFound());
        when(policy.evaluate(VIEWER, OWNER)).thenThrow(new IllegalStateException("synthetic unavailable"));
        mvc.perform(get(path("/shared")).with(actor(VIEWER))).andExpect(status().isNotFound());
        mvc.perform(get(BASE + "/" + UUID.randomUUID() + "/shared").with(actor(VIEWER)))
                .andExpect(status().isNotFound());
    }

    @Test
    void ownershipCsrfAndAggregateVersionAreEnforced() throws Exception {
        var body = rules("PUBLIC", "{}", "{}", 0);
        mvc.perform(get(path("/privacy")).with(actor(VIEWER))).andExpect(status().isNotFound());
        mvc.perform(put(path("/privacy")).with(actor(VIEWER)).with(csrf()).contentType("application/json").content(body))
                .andExpect(status().isNotFound());
        mvc.perform(put(path("/privacy")).with(actor(OWNER)).contentType("application/json").content(body))
                .andExpect(status().isForbidden());
        save("PUBLIC", "{}", "{}", 0);
        mvc.perform(put(path("/privacy")).with(actor(OWNER)).with(csrf()).contentType("application/json").content(body))
                .andExpect(status().isConflict());
        mvc.perform(put(path("")).with(actor(OWNER)).with(csrf()).contentType("application/json")
                .content("{\"profileVersion\":0,\"name\":\"Changed\",\"deviceType\":\"LAPTOP\"}"))
                .andExpect(status().isConflict());
        mvc.perform(get(path("/shared"))).andExpect(status().isUnauthorized());
        mvc.perform(get(path("/shared")).with(user("test").roles("ADMIN"))).andExpect(status().isForbidden());
        mvc.perform(delete(path("")).with(actor(OWNER)).with(csrf()).param("profileVersion", "1"))
                .andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM hardware_profile_field_visibility", Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM hardware_profile_category_visibility", Long.class)).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "{}", "{\"profileVersion\":-1,\"visibility\":\"PUBLIC\",\"fields\":{},\"categories\":{}}",
        "{\"profileVersion\":0,\"visibility\":\"INVALID\",\"fields\":{},\"categories\":{}}",
        "{\"profileVersion\":0,\"visibility\":\"PUBLIC\",\"fields\":{\"UNKNOWN\":\"PUBLIC\"},\"categories\":{}}",
        "{\"profileVersion\":0,\"visibility\":\"PUBLIC\",\"fields\":{\"PROFILE_NAME\":null},\"categories\":{}}",
        "{\"profileVersion\":0,\"visibility\":\"PUBLIC\",\"fields\":{},\"categories\":{\"RAM\":\"INVALID\"}}",
        "{\"profileVersion\":0,\"visibility\":\"PUBLIC\",\"fields\":{},\"categories\":{},\"userId\":\"fake\"}"
    })
    void rejectsMalformedRulesAndMassAssignmentWithoutMutation(String body) throws Exception {
        mvc.perform(put(path("/privacy")).with(actor(OWNER)).with(csrf()).contentType("application/json").content(body))
                .andExpect(status().isBadRequest());
        mvc.perform(get(path("/privacy")).with(actor(OWNER))).andExpect(jsonPath("$.profileVersion").value(0))
                .andExpect(jsonPath("$.visibility").value("PRIVATE"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"-1", "10001"})
    void rejectsUnboundedSharedPage(String page) throws Exception {
        mvc.perform(get(path("/shared")).with(actor(OWNER)).param("page", page)).andExpect(status().isBadRequest());
    }

    @Test
    void concurrentPrivacyAndComponentWritesShareTheParentVersionLock() throws Exception {
        var start = new java.util.concurrent.CountDownLatch(1);
        try (var pool = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var privacy = pool.submit(() -> {
                start.await();
                return mvc.perform(put(path("/privacy")).with(actor(OWNER)).with(csrf())
                        .contentType("application/json").content(rules("PUBLIC", "{}", "{}", 0)))
                        .andReturn().getResponse().getStatus();
            });
            var component = pool.submit(() -> {
                start.await();
                return mvc.perform(post(path("/components")).with(actor(OWNER)).with(csrf())
                        .contentType("application/json").content("{\"profileVersion\":0,\"category\":\"CPU\","
                                + "\"modelName\":\"Race\",\"quantity\":1}"))
                        .andReturn().getResponse().getStatus();
            });
            start.countDown();
            assertThat(List.of(privacy.get(), component.get()))
                    .contains(409).anyMatch(code -> code == 200 || code == 201);
        }
        assertThat(jdbc.queryForObject("SELECT version FROM hardware_profiles WHERE id=?", Long.class, id)).isEqualTo(1);
    }
}
