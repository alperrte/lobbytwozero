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
import static com.ltz.hardware.api.controller.HardwareProfileControllerTest.actor;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"logging.level.root=INFO", "debug=false"})
@AutoConfigureMockMvc
class HardwareComparisonControllerTest extends PostgresIntegrationSupport {
    private static final String BASE = "/api/v1/hardware/comparisons";
    private static final UUID OWNER = UUID.randomUUID();
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    UUID left;
    UUID right;
    UUID other;
    UUID leftComponent;
    UUID rightComponent;

    @BeforeEach
    void prepare() {
        jdbc.update("DELETE FROM hardware_campaigns");
        jdbc.update("DELETE FROM hardware_profiles");
        jdbc.update("DELETE FROM hardware_product_brands");
        jdbc.update("DELETE FROM hardware_products");
        left = profile(OWNER, "DESKTOP");
        right = profile(OWNER, "LAPTOP");
        other = profile(UUID.randomUUID(), "DESKTOP");
        leftComponent = component(left, "RAM", 2, 16);
        rightComponent = component(right, "RAM", 1, null);
        component(right, "STORAGE", 2, 512);
        component(other, "RAM", 128, 1048576);
    }

    private UUID profile(UUID owner, String type) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO hardware_profiles(id,user_id,name,device_type,created_at,updated_at) VALUES (?,?,?, ?,now(),now())",
                id, owner, "Private name", type);
        return id;
    }
    private UUID component(UUID profileId, String category, int quantity, Integer capacity) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO hardware_profile_components(id,profile_id,category,model_name,quantity,capacity_gb,created_at,updated_at)"
                + " VALUES (?,?,?,?,?,?,now(),now())", id, profileId, category, "Recorded model", quantity, capacity);
        return id;
    }
    private UUID product(String category, String status, Integer capacity) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO hardware_products(id,category,model,normalized_model,status,capacity_gb) VALUES (?,?,?,?,?,?)",
                id, category, "Model " + id, "model " + id, status, capacity);
        return id;
    }
    private String ids(UUID a, UUID b) { return "{\"leftId\":\"" + a + "\",\"rightId\":\"" + b + "\"}"; }
    private String references(UUID aProfile, UUID a, UUID bProfile, UUID b) {
        return "{\"left\":{\"profileId\":\"" + aProfile + "\",\"componentId\":\"" + a
                + "\"},\"right\":{\"profileId\":\"" + bProfile + "\",\"componentId\":\"" + b + "\"}}";
    }

    @Test
    void comparesOnlyOwnedRecordedCountsAndSeparatesUnknownCapacity() throws Exception {
        mvc.perform(post(BASE + "/profiles").with(actor(OWNER)).with(csrf()).contentType("application/json").content(ids(left, right)))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store, private"))
                .andExpect(jsonPath("$.left.categories.RAM.entries").value(1))
                .andExpect(jsonPath("$.left.categories.RAM.units").value(2))
                .andExpect(jsonPath("$.left.ram.knownCapacityGb").value(32))
                .andExpect(jsonPath("$.left.ram.unitsWithoutCapacity").value(0))
                .andExpect(jsonPath("$.right.ram.knownCapacityGb").value(0))
                .andExpect(jsonPath("$.right.ram.unitsWithoutCapacity").value(1))
                .andExpect(jsonPath("$.right.storage.knownCapacityGb").value(1024))
                .andExpect(jsonPath("$.left.categories.CPU.entries").value(0))
                .andExpect(jsonPath("$.left.name").doesNotExist()).andExpect(jsonPath("$.left.userId").doesNotExist())
                .andExpect(jsonPath("$.winner").doesNotExist()).andExpect(jsonPath("$.fps").doesNotExist());
        mvc.perform(post(BASE + "/profiles").with(actor(OWNER)).with(csrf()).contentType("application/json").content(ids(left, left)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.right.ram.knownCapacityGb").value(32));
    }

    @Test
    void profileComparisonNeverAcceptsOtherOwnerEvenIfPublic() throws Exception {
        jdbc.update("UPDATE hardware_profiles SET visibility='PUBLIC' WHERE id=?", other);
        for (UUID forbidden : List.of(other, UUID.randomUUID())) {
            mvc.perform(post(BASE + "/profiles").with(actor(OWNER)).with(csrf()).contentType("application/json")
                    .content(ids(left, forbidden))).andExpect(status().isNotFound());
            mvc.perform(post(BASE + "/profiles").with(actor(OWNER)).with(csrf()).contentType("application/json")
                    .content(ids(forbidden, left))).andExpect(status().isNotFound());
        }
    }

    @Test
    void comparesRawOwnedComponentsAndUnknownSpecificationsStayUnknown() throws Exception {
        mvc.perform(post(BASE + "/components").with(actor(OWNER)).with(csrf()).contentType("application/json")
                .content(references(left, leftComponent, right, rightComponent)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.left.quantity").value(2))
                .andExpect(jsonPath("$.left.specifications.capacityGb").value(16))
                .andExpect(jsonPath("$.right.specifications").isEmpty())
                .andExpect(jsonPath("$.left.specifications.clockMhz").isEmpty());
    }

    @Test
    void rejectsComponentCrossProfileCrossOwnerAndMismatchedCategory() throws Exception {
        var wrongCategory = component(right, "CPU", 1, 8);
        var forbidden = component(other, "RAM", 1, 16);
        for (var body : List.of(references(left, leftComponent, other, forbidden),
                references(left, leftComponent, right, leftComponent),
                references(left, UUID.randomUUID(), right, rightComponent))) {
            mvc.perform(post(BASE + "/components").with(actor(OWNER)).with(csrf()).contentType("application/json")
                    .content(body)).andExpect(status().isNotFound());
        }
        mvc.perform(post(BASE + "/components").with(actor(OWNER)).with(csrf()).contentType("application/json")
                .content(references(left, leftComponent, right, wrongCategory))).andExpect(status().isBadRequest());
    }

    @Test
    void comparesActiveGeneralModelsWithoutCatalogWritePermission() throws Exception {
        var a = product("GPU", "ACTIVE", 12);
        var b = product("GPU", "ACTIVE", null);
        mvc.perform(post(BASE + "/catalog-products").with(actor(OWNER)).with(csrf()).contentType("application/json").content(ids(a, b)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.left.category").value("GPU"))
                .andExpect(jsonPath("$.left.specifications.capacityGb").value(12))
                .andExpect(jsonPath("$.right.specifications").isEmpty()).andExpect(jsonPath("$.left.quantity").isEmpty());
        var archived = product("GPU", "ARCHIVED", 16);
        mvc.perform(post(BASE + "/catalog-products").with(actor(OWNER)).with(csrf()).contentType("application/json").content(ids(a, archived)))
                .andExpect(status().isNotFound());
        var cpu = product("CPU", "ACTIVE", null);
        mvc.perform(post(BASE + "/catalog-products").with(actor(OWNER)).with(csrf()).contentType("application/json").content(ids(a, cpu)))
                .andExpect(status().isBadRequest());
        var absent = UUID.randomUUID();
        mvc.perform(post(BASE + "/catalog-products").with(actor(OWNER)).with(csrf()).contentType("application/json").content(ids(a, absent)))
                .andExpect(status().isNotFound());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/profiles", "/components", "/catalog-products"})
    void allComparisonEndpointsRequireTrustedActorAndCsrf(String suffix) throws Exception {
        mvc.perform(post(BASE + suffix).with(csrf()).header("X-User-Id", OWNER).contentType("application/json").content("{}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post(BASE + suffix).with(actor(OWNER)).contentType("application/json").content("{}"))
                .andExpect(status().isForbidden());
        mvc.perform(post(BASE + suffix).with(user("test").roles("ADMIN")).with(csrf()).contentType("application/json").content("{}"))
                .andExpect(status().isForbidden());
        mvc.perform(post(BASE + suffix).with(actor(OWNER)).with(csrf()).contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void nestedValidationAndMassAssignmentAreRejected() throws Exception {
        mvc.perform(post(BASE + "/components").with(actor(OWNER)).with(csrf()).contentType("application/json")
                .content("{\"left\":{},\"right\":{}}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        mvc.perform(post(BASE + "/profiles").with(actor(OWNER)).with(csrf()).contentType("application/json")
                .content(ids(left, right).replace("}", ",\"userId\":\"fake\"}")))
                .andExpect(status().isBadRequest());
    }
}
