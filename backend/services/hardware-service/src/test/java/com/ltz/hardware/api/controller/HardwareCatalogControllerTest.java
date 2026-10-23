package com.ltz.hardware.api.controller;

import com.ltz.hardware.application.port.AuthenticatedActor;
import com.ltz.hardware.application.port.CatalogWriteAccess;
import com.ltz.hardware.domain.enums.ComponentCategory;
import com.ltz.hardware.infrastructure.persistence.PostgresIntegrationSupport;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.ObjectMapper;
import static com.ltz.hardware.api.controller.HardwareProfileControllerTest.actor;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"logging.level.root=INFO", "debug=false"})
@AutoConfigureMockMvc
class HardwareCatalogControllerTest extends PostgresIntegrationSupport {
    static final String BASE = "/api/v1/hardware/catalog";
    final UUID manager = UUID.randomUUID();
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;

    @BeforeEach
    void clear() {
        jdbc.update("DELETE FROM hardware_campaigns");
        jdbc.update("DELETE FROM hardware_profiles");
        jdbc.update("DELETE FROM hardware_product_brands");
        jdbc.update("DELETE FROM hardware_products");
        jdbc.update("DELETE FROM hardware_brands");
        // Audit history deliberately survives fixture cleanup.
    }

    static RequestPostProcessor writer(UUID id) {
        return authentication(new UsernamePasswordAuthenticationToken(new AuthenticatedActor(id), null,
                List.of(new SimpleGrantedAuthority(CatalogWriteAccess.PERMISSION))));
    }

    String json(Object value) { return mapper.writeValueAsString(value); }

    UUID brand(String name) throws Exception {
        var response = mvc.perform(post(BASE + "/brands").with(writer(manager)).with(csrf())
                .contentType("application/json").content(json(Map.of("name", name, "reason", "Synthetic catalog update"))))
                .andExpect(status().isCreated()).andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(mapper.readTree(response).get("id").asText());
    }

    UUID product(String model, UUID... brands) throws Exception {
        var response = mvc.perform(post(BASE + "/products").with(writer(manager)).with(csrf())
                .contentType("application/json").content(json(Map.of("category", "GPU", "model", model,
                        "brandIds", List.of(brands), "reason", "Synthetic model"))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return UUID.fromString(mapper.readTree(response).get("id").asText());
    }

    String archiveBody(long version) { return json(Map.of("version", version, "reason", "Synthetic archive")); }

    @ParameterizedTest
    @EnumSource(ComponentCategory.class)
    void createsBrandIndependentModelsAcrossAllCategories(ComponentCategory category) throws Exception {
        mvc.perform(post(BASE + "/products").with(writer(manager)).with(csrf()).contentType("application/json")
                .content(json(Map.of("category", category, "model", "General model", "reason", "Synthetic model"))))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.brands").isEmpty())
                .andExpect(jsonPath("$.category").value(category.name()))
                .andExpect(jsonPath("$.specifications").isEmpty()).andExpect(jsonPath("$.version").value(0))
                .andExpect(header().string("Cache-Control", "no-store, private"))
                .andExpect(jsonPath("$.userId").doesNotExist());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM hardware_catalog_audit WHERE actor_id = ?",
                Long.class, manager)).isEqualTo(1);
    }

    @Test
    void createsOptionalMultipleManufacturersAndRetainsBrandFreeSelection() throws Exception {
        UUID a = brand("Maker A");
        UUID b = brand("Maker B");
        UUID id = product("General GPU", b, a);
        mvc.perform(get(BASE + "/products/" + id).with(actor(UUID.randomUUID())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.brands.length()").value(2))
                .andExpect(jsonPath("$.brands[0].name").value("Maker A"));
        mvc.perform(get(BASE + "/brands/" + a).with(actor(UUID.randomUUID())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Maker A"));
        mvc.perform(get(BASE + "/products").with(actor(UUID.randomUUID())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].brands.length()").value(2));
        String profile = mvc.perform(post("/api/v1/hardware/profiles").with(actor(manager)).with(csrf())
                .contentType("application/json").content("{\"name\":\"PC\",\"deviceType\":\"DESKTOP\"}"))
                .andReturn().getResponse().getContentAsString();
        String profileId = mapper.readTree(profile).get("id").asText();
        mvc.perform(post("/api/v1/hardware/profiles/" + profileId + "/components").with(actor(manager)).with(csrf())
                .contentType("application/json").content(json(Map.of("profileVersion", 0, "category", "GPU",
                        "productId", id, "quantity", 1))))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.component.brandId").isEmpty());
    }

    @Test
    void normalizesNamesAndPreventsDuplicateGeneralModelsRegardlessOfBrand() throws Exception {
        UUID brand = brand("  Test   Maker  ");
        mvc.perform(post(BASE + "/brands").with(writer(manager)).with(csrf()).contentType("application/json")
                .content(json(Map.of("name", "test maker", "reason", "Duplicate"))))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DATA_CONFLICT"));
        product("  General  GPU  ");
        mvc.perform(post(BASE + "/products").with(writer(manager)).with(csrf()).contentType("application/json")
                .content(json(Map.of("category", "GPU", "model", "general gpu", "brandIds", List.of(brand),
                        "reason", "Duplicate brand-independent model"))))
                .andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM hardware_catalog_audit WHERE actor_id = ?",
                Long.class, manager)).isEqualTo(2);
    }

    @Test
    void rejectsMissingBrandWithoutPartialProductOrAudit() throws Exception {
        mvc.perform(post(BASE + "/products").with(writer(manager)).with(csrf()).contentType("application/json")
                .content(json(Map.of("category", "CPU", "model", "CPU", "brandIds", List.of(UUID.randomUUID()),
                        "reason", "Synthetic")))).andExpect(status().isNotFound());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM hardware_products", Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM hardware_catalog_audit WHERE actor_id = ?",
                Long.class, manager)).isZero();
    }

    @Test
    void updatesModelAndOptionsWithVersionAndClearsOmittedSpecifications() throws Exception {
        UUID b = brand("Maker");
        UUID id = product("Old", b);
        mvc.perform(put(BASE + "/products/" + id).with(writer(manager)).with(csrf()).contentType("application/json")
                .content(json(Map.of("version", 0, "model", "New", "specifications", Map.of("capacityGb", 16),
                        "reason", "Correct known capacity"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.brands").isEmpty())
                .andExpect(jsonPath("$.specifications.capacityGb").value(16));
        mvc.perform(put(BASE + "/products/" + id).with(writer(manager)).with(csrf()).contentType("application/json")
                .content(json(Map.of("version", 0, "model", "Stale", "reason", "Stale"))))
                .andExpect(status().isConflict());
        mvc.perform(put(BASE + "/products/" + id).with(writer(manager)).with(csrf()).contentType("application/json")
                .content(json(Map.of("version", 1, "model", "New", "reason", "Remove unknown specs"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(2))
                .andExpect(jsonPath("$.specifications").isEmpty());
    }

    @Test
    void updatesBrandAndRejectsStaleArchiveAndUnknownIds() throws Exception {
        UUID id = brand("Old");
        mvc.perform(put(BASE + "/brands/" + id).with(writer(manager)).with(csrf()).contentType("application/json")
                .content(json(Map.of("version", 0, "name", "Updated", "reason", "Rename"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1));
        mvc.perform(delete(BASE + "/brands/" + id).with(writer(manager)).with(csrf())
                .contentType("application/json").content(archiveBody(0))).andExpect(status().isConflict());
        mvc.perform(put(BASE + "/brands/" + id).with(writer(manager)).with(csrf()).contentType("application/json")
                .content(json(Map.of("version", 0, "name", "Stale", "reason", "Stale"))))
                .andExpect(status().isConflict());
        for (String path : List.of("/brands/", "/products/")) {
            mvc.perform(get(BASE + path + UUID.randomUUID()).with(actor(manager))).andExpect(status().isNotFound());
            mvc.perform(delete(BASE + path + UUID.randomUUID()).with(writer(manager)).with(csrf())
                    .contentType("application/json").content(archiveBody(0))).andExpect(status().isNotFound());
        }
    }

    @Test
    void archiveHidesNewSelectionsButPreservesExistingProfileSnapshots() throws Exception {
        UUID b = brand("Maker");
        UUID id = product("Old GPU", b);
        UUID profile = UUID.randomUUID();
        UUID component = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO hardware_profiles(id,user_id,name,device_type,created_at,updated_at)
                VALUES (?,?,'PC','DESKTOP',now(),now())
                """, profile, manager);
        jdbc.update("""
                INSERT INTO hardware_profile_components(id,profile_id,product_id,brand_id,category,model_name,
                  brand_name,quantity,created_at,updated_at) VALUES (?,?,?,?,'GPU','Old GPU','Maker',1,now(),now())
                """, component, profile, id, b);
        mvc.perform(put(BASE + "/products/" + id).with(writer(manager)).with(csrf()).contentType("application/json")
                .content(json(Map.of("version", 0, "model", "Changed", "reason", "Try unlink used option"))))
                .andExpect(status().isConflict());
        mvc.perform(delete(BASE + "/brands/" + b).with(writer(manager)).with(csrf())
                .contentType("application/json").content(archiveBody(0))).andExpect(status().isNoContent());
        mvc.perform(get(BASE + "/products/" + id).with(actor(manager)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.brands").isEmpty());
        mvc.perform(post("/api/v1/hardware/profiles/" + profile + "/components").with(actor(manager)).with(csrf())
                .contentType("application/json").content(json(Map.of("profileVersion", 0, "category", "GPU",
                        "productId", id, "brandId", b, "quantity", 1)))).andExpect(status().isNotFound());
        mvc.perform(delete(BASE + "/products/" + id).with(writer(manager)).with(csrf())
                .contentType("application/json").content(archiveBody(0))).andExpect(status().isNoContent());
        mvc.perform(get(BASE + "/products/" + id).with(actor(manager))).andExpect(status().isNotFound());
        mvc.perform(get(BASE + "/brands/" + b).with(actor(manager))).andExpect(status().isNotFound());
        mvc.perform(get(BASE + "/products").with(actor(manager)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(post("/api/v1/hardware/profiles/" + profile + "/components").with(actor(manager)).with(csrf())
                .contentType("application/json").content(json(Map.of("profileVersion", 0, "category", "GPU",
                        "productId", id, "quantity", 1)))).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/hardware/profiles/" + profile).with(actor(manager)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.components[0].modelName").value("Old GPU"))
                .andExpect(jsonPath("$.components[0].brandName").value("Maker"));
        mvc.perform(delete(BASE + "/products/" + id).with(writer(manager)).with(csrf())
                .contentType("application/json").content(archiveBody(1))).andExpect(status().isConflict());
        mvc.perform(put(BASE + "/brands/" + b).with(writer(manager)).with(csrf()).contentType("application/json")
                .content(json(Map.of("version", 1, "name", "Revive", "reason", "Not supported"))))
                .andExpect(status().isConflict());
    }

    @Test
    void disallowsAnonymousOrdinaryUserAndSpoofedPrincipalForWrites() throws Exception {
        String body = json(Map.of("name", "Maker", "reason", "Synthetic"));
        mvc.perform(get(BASE + "/brands")).andExpect(status().isUnauthorized());
        mvc.perform(get(BASE + "/products").header("Authorization", "Bearer untrusted")
                .header("X-User-Id", manager).header("X-Role", "ADMIN")).andExpect(status().isUnauthorized());
        mvc.perform(post(BASE + "/brands").with(actor(manager)).with(csrf()).contentType("application/json")
                .content(body)).andExpect(status().isForbidden());
        mvc.perform(post(BASE + "/brands").with(writer(manager)).contentType("application/json")
                .content(body)).andExpect(status().isForbidden());
        mvc.perform(post(BASE + "/brands").with(user("fake").authorities(new SimpleGrantedAuthority(CatalogWriteAccess.PERMISSION)))
                .with(csrf()).contentType("application/json").content(body)).andExpect(status().isForbidden());
        mvc.perform(post(BASE + "/products").with(actor(manager)).with(csrf()).contentType("application/json")
                .content("{}")).andExpect(status().isForbidden());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM hardware_brands", Long.class)).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"name\":\" \",\"reason\":\"Synthetic\"}",
            "{\"name\":\"<b>Maker</b>\",\"reason\":\"Synthetic\"}", "{\"name\":\"Maker\",\"reason\":\" \"}",
            "{\"name\":\"Maker\",\"reason\":\"Synthetic\",\"userId\":\"spoof\"}",
            "{\"name\":\"Maker\",\"reason\":\"Synthetic\",\"status\":\"ACTIVE\"}",
            "{\"name\":\"＜b＞Maker＜/b＞\",\"reason\":\"Synthetic\"}"})
    void validatesBrandMutationAndRejectsMassAssignment(String body) throws Exception {
        mvc.perform(post(BASE + "/brands").with(writer(manager)).with(csrf())
                .contentType("application/json").content(body)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"category\":\"INVALID\",\"model\":\"x\",\"reason\":\"Synthetic\"}",
            "{\"category\":\"CPU\",\"model\":\"x\",\"reason\":\"Synthetic\",\"specifications\":{\"coreCount\":0}}",
            "{\"category\":\"CPU\",\"model\":\"x\",\"reason\":\"Synthetic\",\"brandIds\":[null]}"})
    void validatesProducts(String body) throws Exception {
        mvc.perform(post(BASE + "/products").with(writer(manager)).with(csrf())
                .contentType("application/json").content(body)).andExpect(status().isBadRequest());
    }

    @Test
    void boundsBodyPaginationAndUsesStableOrdering() throws Exception {
        brand("Zulu");
        brand("Alpha");
        mvc.perform(get(BASE + "/brands").with(actor(manager)).param("size", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].name").value("Alpha"))
                .andExpect(jsonPath("$.totalPages").value(2));
        for (String path : List.of("/brands", "/products")) {
            for (String size : List.of("0", "101")) {
                mvc.perform(get(BASE + path).with(actor(manager)).param("size", size)).andExpect(status().isBadRequest());
            }
            mvc.perform(get(BASE + path).with(actor(manager)).param("page", "-1")).andExpect(status().isBadRequest());
            mvc.perform(get(BASE + path).with(actor(manager)).param("page", "10001")).andExpect(status().isBadRequest());
        }
        mvc.perform(post(BASE + "/brands").with(writer(manager)).with(csrf()).contentType("application/json")
                .content(json(Map.of("name", "x".repeat(17000), "reason", "Large")))
                .with(request -> { request.removeHeader("Content-Length"); return request; }))
                .andExpect(status().is(413)).andExpect(jsonPath("$.code").value("REQUEST_TOO_LARGE"));
    }

    @Test
    void auditIsAppendOnlyAndRateLimitRollsBackMutation() throws Exception {
        UUID target = brand("Maker");
        assertThatThrownBy(() -> jdbc.update("UPDATE hardware_catalog_audit SET reason='changed' WHERE target_id=?", target))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("DELETE FROM hardware_catalog_audit WHERE target_id=?", target))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.execute("TRUNCATE hardware_catalog_audit"))
                .isInstanceOf(DataIntegrityViolationException.class);
        jdbc.update("""
                INSERT INTO hardware_catalog_audit(id,actor_id,action,target_id,reason)
                SELECT gen_random_uuid(),?,'CREATE_BRAND',?,'Synthetic rate fixture' FROM generate_series(1,59)
                """, manager, target);
        mvc.perform(post(BASE + "/brands").with(writer(manager)).with(csrf()).contentType("application/json")
                .content(json(Map.of("name", "Rate limited", "reason", "Synthetic"))))
                .andExpect(status().isTooManyRequests()).andExpect(header().string("Retry-After", "60"))
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM hardware_brands", Long.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM hardware_catalog_audit WHERE actor_id=?",
                Long.class, manager)).isEqualTo(60);
    }

    @Test
    void concurrentUpdatesWithSameVersionHaveOneWinner() throws Exception {
        UUID id = product("Original");
        var gate = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var tasks = java.util.stream.IntStream.range(0, 2).mapToObj(i -> pool.submit(() -> {
                gate.await();
                return mvc.perform(put(BASE + "/products/" + id).with(writer(manager)).with(csrf())
                        .contentType("application/json").content(json(Map.of("version", 0, "model", "Writer " + i,
                                "reason", "Concurrent fixture")))).andReturn().getResponse().getStatus();
            })).toList();
            gate.countDown();
            assertThat(List.of(tasks.get(0).get(20, TimeUnit.SECONDS), tasks.get(1).get(20, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(200, 409);
        }
        assertThat(jdbc.queryForObject("SELECT version FROM hardware_products WHERE id=?", Long.class, id)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM hardware_catalog_audit WHERE actor_id=?",
                Long.class, manager)).isEqualTo(2);
    }
}
