package com.ltz.hardware.api.controller;

import com.ltz.hardware.domain.enums.ComponentCategory;
import com.ltz.hardware.infrastructure.persistence.PostgresIntegrationSupport;
import java.util.UUID;
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
import org.springframework.test.web.servlet.MockMvc;
import static com.ltz.hardware.api.controller.HardwareProfileControllerTest.actor;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"logging.level.root=INFO", "debug=false"})
@AutoConfigureMockMvc
class HardwareComponentControllerTest extends PostgresIntegrationSupport {
    private static final String BASE = "/api/v1/hardware/profiles";
    private static final UUID OWNER = UUID.randomUUID();
    private static final UUID OTHER = UUID.randomUUID();
    private static final UUID PRODUCT = UUID.randomUUID();
    private static final UUID BRAND = UUID.randomUUID();
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    UUID profileId;

    @BeforeEach
    void prepare() throws Exception {
        jdbc.update("DELETE FROM hardware_campaigns");
        jdbc.update("DELETE FROM hardware_profiles");
        jdbc.update("DELETE FROM hardware_product_brands");
        jdbc.update("DELETE FROM hardware_products");
        jdbc.update("DELETE FROM hardware_brands");
        mvc.perform(post(BASE).with(actor(OWNER)).with(csrf()).contentType("application/json")
                .content("{\"name\":\"PC\",\"deviceType\":\"DESKTOP\"}")).andExpect(status().isCreated());
        profileId = jdbc.queryForObject("SELECT id FROM hardware_profiles", UUID.class);
        jdbc.update("INSERT INTO hardware_products(id,category,model,normalized_model,capacity_gb) VALUES (?,?,?,?,?)",
                PRODUCT, "GPU", "Generic GPU", "generic gpu", 12);
        jdbc.update("INSERT INTO hardware_brands(id,name,normalized_name) VALUES (?,?,?)", BRAND, "Test Brand", "test brand");
        jdbc.update("INSERT INTO hardware_product_brands(product_id,brand_id) VALUES (?,?)", PRODUCT, BRAND);
    }

    String path() { return BASE + "/" + profileId + "/components"; }

    @ParameterizedTest
    @EnumSource(ComponentCategory.class)
    void acceptsManualComponentsForEverySupportedCategory(ComponentCategory category) throws Exception {
        mvc.perform(post(path()).with(actor(OWNER)).with(csrf()).contentType("application/json")
                        .content("{\"profileVersion\":0,\"category\":\"" + category
                                + "\",\"modelName\":\"Manual part\",\"brandName\":\"Optional brand\",\"quantity\":1}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.profileVersion").value(1))
                .andExpect(jsonPath("$.component.modelName").value("Manual part"))
                .andExpect(jsonPath("$.component.productId").isEmpty())
                .andExpect(jsonPath("$.component.source").value("MANUAL"));
    }

    @Test
    void selectsGeneralModelWithoutBrandAndCopiesRecordedSpecifications() throws Exception {
        mvc.perform(post(path()).with(actor(OWNER)).with(csrf()).contentType("application/json")
                        .content("{\"profileVersion\":0,\"category\":\"GPU\",\"productId\":\"" + PRODUCT + "\",\"quantity\":1}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.component.brandId").isEmpty())
                .andExpect(jsonPath("$.component.brandName").isEmpty())
                .andExpect(jsonPath("$.component.modelName").value("Generic GPU"))
                .andExpect(jsonPath("$.component.specifications.capacityGb").value(12))
                .andExpect(jsonPath("$.component.specifications.clockMhz").isEmpty());
    }

    @Test
    void selectsOptionalManufacturerAndValidatesUserSpecifications() throws Exception {
        mvc.perform(post(path()).with(actor(OWNER)).with(csrf()).contentType("application/json")
                        .content("{\"profileVersion\":0,\"category\":\"GPU\",\"productId\":\"" + PRODUCT
                                + "\",\"brandId\":\"" + BRAND + "\",\"quantity\":2,\"specifications\":{\"capacityGb\":16}}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.component.brandName").value("Test Brand"))
                .andExpect(jsonPath("$.component.quantity").value(2))
                .andExpect(jsonPath("$.component.specifications.capacityGb").value(16));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"profileVersion\":0,\"category\":\"CPU\",\"quantity\":1}",
            "{\"profileVersion\":0,\"category\":\"CPU\",\"modelName\":\"x\",\"quantity\":0}",
            "{\"profileVersion\":0,\"category\":\"CPU\",\"modelName\":\"x\",\"quantity\":129}",
            "{\"profileVersion\":0,\"category\":\"CPU\",\"modelName\":\"x\",\"quantity\":1,\"specifications\":{\"coreCount\":0}}",
            "{\"profileVersion\":0,\"category\":\"RAM\",\"modelName\":\"x\",\"quantity\":1,\"specifications\":{\"capacityGb\":-1}}",
            "{\"category\":\"CPU\",\"modelName\":\"x\",\"quantity\":1}"})
    void rejectsInvalidRequestsWithoutPartialWrites(String body) throws Exception {
        mvc.perform(post(path()).with(actor(OWNER)).with(csrf()).contentType("application/json").content(body))
                .andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM hardware_profile_components", Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT version FROM hardware_profiles", Long.class)).isZero();
    }

    @Test
    void rejectsCategoryMismatchMixedFieldsUnsupportedBrandAndMissingProduct() throws Exception {
        String common = "{\"profileVersion\":0,\"quantity\":1,\"productId\":\"" + PRODUCT + "\",";
        for (String suffix : new String[]{"\"category\":\"CPU\"}", "\"category\":\"GPU\",\"modelName\":\"fake\"}",
                "\"category\":\"GPU\",\"brandId\":\"" + UUID.randomUUID() + "\"}"}) {
            mvc.perform(post(path()).with(actor(OWNER)).with(csrf()).contentType("application/json").content(common + suffix))
                    .andExpect(status().isBadRequest());
        }
        mvc.perform(post(path()).with(actor(OWNER)).with(csrf()).contentType("application/json")
                        .content("{\"profileVersion\":0,\"category\":\"GPU\",\"quantity\":1,\"productId\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void rejectsOtherOwnerAndStaleVersion() throws Exception {
        var body = "{\"profileVersion\":0,\"category\":\"CPU\",\"modelName\":\"CPU\",\"quantity\":1}";
        mvc.perform(post(path()).with(actor(OTHER)).with(csrf()).contentType("application/json").content(body))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("HARDWARE_NOT_FOUND"));
        mvc.perform(post(path()).with(actor(OWNER)).with(csrf()).contentType("application/json").content(body))
                .andExpect(status().isCreated());
        mvc.perform(post(path()).with(actor(OWNER)).with(csrf()).contentType("application/json").content(body))
                .andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM hardware_profile_components", Long.class)).isEqualTo(1);
    }

    @Test
    void generalModelUniquenessDoesNotDependOnNullableBrand() {
        UUID duplicateId = UUID.randomUUID();
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO hardware_products(id,category,model,normalized_model) VALUES (?,?,?,?)",
                duplicateId, "GPU", "Generic GPU", "generic gpu"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    UUID addManual() throws Exception {
        mvc.perform(post(path()).with(actor(OWNER)).with(csrf()).contentType("application/json")
                .content("{\"profileVersion\":0,\"category\":\"CPU\",\"modelName\":\"Old CPU\",\"quantity\":1}"))
                .andExpect(status().isCreated());
        return jdbc.queryForObject("SELECT id FROM hardware_profile_components", UUID.class);
    }

    @Test
    void replacesComponentWithoutChangingIdentityAndRejectsStaleOrOtherOwner() throws Exception {
        var id = addManual();
        var body = "{\"profileVersion\":1,\"category\":\"RAM\",\"modelName\":\"New RAM\",\"quantity\":2,"
                + "\"specifications\":{\"capacityGb\":16,\"clockMhz\":6000,\"memoryType\":\"DDR5\"}}";
        mvc.perform(put(path() + "/" + id).with(actor(OTHER)).with(csrf()).contentType("application/json").content(body))
                .andExpect(status().isNotFound());
        mvc.perform(put(path() + "/" + id).with(actor(OWNER)).with(csrf()).contentType("application/json").content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.profileVersion").value(2))
                .andExpect(jsonPath("$.component.id").value(id.toString()))
                .andExpect(jsonPath("$.component.version").value(1))
                .andExpect(jsonPath("$.component.specifications.memoryType").value("DDR5"));
        mvc.perform(put(path() + "/" + id).with(actor(OWNER)).with(csrf()).contentType("application/json").content(body))
                .andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("SELECT model_name FROM hardware_profile_components", String.class)).isEqualTo("New RAM");
    }

    @Test
    void removesOnlyOwnedComponentWithCurrentProfileVersion() throws Exception {
        var id = addManual();
        mvc.perform(delete(path() + "/" + id).param("profileVersion", "1").with(actor(OTHER)).with(csrf()))
                .andExpect(status().isNotFound());
        mvc.perform(delete(path() + "/" + id).param("profileVersion", "0").with(actor(OWNER)).with(csrf()))
                .andExpect(status().isConflict());
        mvc.perform(delete(path() + "/" + id).param("profileVersion", "1").with(actor(OWNER)).with(csrf()))
                .andExpect(status().isNoContent()).andExpect(header().string("X-Profile-Version", "2"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM hardware_profile_components", Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM hardware_profiles", Long.class)).isEqualTo(1);
    }

    @Test
    void concurrentWritersWithSameVersionCannotLoseAnUpdate() throws Exception {
        var start = new java.util.concurrent.CountDownLatch(1);
        try (var executor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            java.util.concurrent.Callable<Integer> write = () -> {
                start.await();
                return mvc.perform(post(path()).with(actor(OWNER)).with(csrf()).contentType("application/json")
                        .content("{\"profileVersion\":0,\"category\":\"CPU\",\"modelName\":\"CPU\",\"quantity\":1}"))
                        .andReturn().getResponse().getStatus();
            };
            var first = executor.submit(write);
            var second = executor.submit(write);
            start.countDown();
            assertThat(java.util.List.of(first.get(10, java.util.concurrent.TimeUnit.SECONDS),
                    second.get(10, java.util.concurrent.TimeUnit.SECONDS))).containsExactlyInAnyOrder(201, 409);
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM hardware_profile_components", Long.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT version FROM hardware_profiles", Long.class)).isEqualTo(1);
    }

    @Test
    void rejectsComponentFromAnotherProfileAndRollsBackInvalidReplacement() throws Exception {
        var id = addManual();
        var another = UUID.randomUUID();
        jdbc.update("INSERT INTO hardware_profiles(id,user_id,name,device_type,created_at,updated_at) VALUES (?,?,?,?,now(),now())",
                another, OWNER, "Other PC", "LAPTOP");
        var valid = "{\"profileVersion\":0,\"category\":\"CPU\",\"modelName\":\"x\",\"quantity\":1}";
        mvc.perform(put(BASE + "/" + another + "/components/" + id).with(actor(OWNER)).with(csrf())
                .contentType("application/json").content(valid)).andExpect(status().isNotFound());
        mvc.perform(put(path() + "/" + id).with(actor(OWNER)).with(csrf()).contentType("application/json")
                        .content("{\"profileVersion\":1,\"category\":\"CPU\",\"quantity\":1}"))
                .andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("SELECT model_name FROM hardware_profile_components WHERE id=?", String.class, id))
                .isEqualTo("Old CPU");
    }

    @Test
    void readsOnlyOwnedRecordedDetailsWithBoundedComponentPages() throws Exception {
        addManual();
        mvc.perform(post(path()).with(actor(OWNER)).with(csrf()).contentType("application/json")
                .content("{\"profileVersion\":1,\"category\":\"RAM\",\"modelName\":\"RAM\",\"quantity\":2,"
                        + "\"specifications\":{\"capacityGb\":16,\"clockMhz\":6000,\"memoryType\":\"DDR5\"}}"))
                .andExpect(status().isCreated());
        mvc.perform(get(BASE + "/" + profileId).with(actor(OWNER)).param("size", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalComponents").value(2))
                .andExpect(jsonPath("$.profile.version").value(2))
                .andExpect(jsonPath("$.components.length()").value(1))
                .andExpect(jsonPath("$.components[0].modelName").value("Old CPU"))
                .andExpect(jsonPath("$.components[0].specifications").isEmpty());
        mvc.perform(get(BASE + "/" + profileId).with(actor(OWNER)).param("size", "1").param("page", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.components[0].specifications.capacityGb").value(16))
                .andExpect(jsonPath("$.components[0].specifications.memoryType").value("DDR5"))
                .andExpect(jsonPath("$.components[0].specifications.coreCount").isEmpty());
        mvc.perform(get(BASE + "/" + profileId).with(actor(OTHER))).andExpect(status().isNotFound());
        mvc.perform(get(BASE + "/" + UUID.randomUUID()).with(actor(OWNER))).andExpect(status().isNotFound());
        mvc.perform(get(BASE + "/" + profileId)).andExpect(status().isUnauthorized());
        mvc.perform(get(BASE + "/" + profileId).with(actor(OWNER)).param("size", "101"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void emptyProfileDoesNotInventTechnicalValues() throws Exception {
        mvc.perform(get(BASE + "/" + profileId).with(actor(OWNER)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalComponents").value(0))
                .andExpect(jsonPath("$.components").isEmpty());
    }

    @Test
    void statisticsUseOnlyRecordedOwnerDataAndDistinguishMissingCapacity() throws Exception {
        String[] bodies = {
            "{\"profileVersion\":0,\"category\":\"RAM\",\"modelName\":\"RAM\",\"quantity\":2,\"specifications\":{\"capacityGb\":16}}",
            "{\"profileVersion\":1,\"category\":\"RAM\",\"modelName\":\"Unknown RAM\",\"quantity\":1}",
            "{\"profileVersion\":2,\"category\":\"STORAGE\",\"modelName\":\"SSD\",\"quantity\":1,\"specifications\":{\"capacityGb\":1024}}",
            "{\"profileVersion\":3,\"category\":\"STORAGE\",\"modelName\":\"Unknown Disk\",\"quantity\":2}"};
        for (var body : bodies) {
            mvc.perform(post(path()).with(actor(OWNER)).with(csrf()).contentType("application/json").content(body))
                    .andExpect(status().isCreated());
        }
        var foreign = UUID.randomUUID();
        jdbc.update("INSERT INTO hardware_profiles(id,user_id,name,device_type,created_at,updated_at) VALUES (?,?,?,?,now(),now())",
                foreign, OTHER, "Private PC", "OTHER");
        jdbc.update("INSERT INTO hardware_profile_components(id,profile_id,category,model_name,quantity,capacity_gb,created_at,updated_at)"
                + " VALUES (?,?,'RAM','Private RAM',100,1024,now(),now())", UUID.randomUUID(), foreign);
        mvc.perform(get(BASE + "/statistics").with(actor(OWNER)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalProfiles").value(1))
                .andExpect(jsonPath("$.profilesByDeviceType.DESKTOP").value(1))
                .andExpect(jsonPath("$.profilesByDeviceType.OTHER").value(0))
                .andExpect(jsonPath("$.totalComponentEntries").value(4))
                .andExpect(jsonPath("$.totalComponentUnits").value(6))
                .andExpect(jsonPath("$.componentsByCategory.RAM.entries").value(2))
                .andExpect(jsonPath("$.componentsByCategory.RAM.units").value(3))
                .andExpect(jsonPath("$.recordedRamCapacityGb").value(32))
                .andExpect(jsonPath("$.ramUnitsWithoutCapacity").value(1))
                .andExpect(jsonPath("$.recordedStorageCapacityGb").value(1024))
                .andExpect(jsonPath("$.storageUnitsWithoutCapacity").value(2));
        mvc.perform(get(BASE + "/statistics").with(actor(UUID.randomUUID())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalProfiles").value(0))
                .andExpect(jsonPath("$.totalComponentEntries").value(0))
                .andExpect(jsonPath("$.recordedRamCapacityGb").value(0));
        mvc.perform(get(BASE + "/statistics")).andExpect(status().isUnauthorized());
    }

    @Test
    void deletesOnlyOwnedProfileAndCascadesComponentsWithoutRemovingCatalogOrOtherProfiles() throws Exception {
        addManual();
        var foreign = UUID.randomUUID();
        jdbc.update("INSERT INTO hardware_profiles(id,user_id,name,device_type,created_at,updated_at) VALUES (?,?,?,?,now(),now())",
                foreign, OTHER, "Private PC", "LAPTOP");
        var foreignComponent = UUID.randomUUID();
        jdbc.update("INSERT INTO hardware_profile_components(id,profile_id,category,model_name,quantity,created_at,updated_at)"
                + " VALUES (?,?,'CPU','Private CPU',1,now(),now())", foreignComponent, foreign);
        mvc.perform(delete(BASE + "/" + profileId).with(actor(OTHER)).with(csrf()).param("profileVersion", "1"))
                .andExpect(status().isNotFound());
        mvc.perform(delete(BASE + "/" + profileId).with(actor(OWNER)).with(csrf()).param("profileVersion", "0"))
                .andExpect(status().isConflict());
        mvc.perform(delete(BASE + "/" + profileId).with(actor(OWNER)).with(csrf()).param("profileVersion", "1"))
                .andExpect(status().isNoContent());
        assertThat(jdbc.queryForList("SELECT id FROM hardware_profiles", UUID.class)).containsExactly(foreign);
        assertThat(jdbc.queryForList("SELECT id FROM hardware_profile_components", UUID.class)).containsExactly(foreignComponent);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM hardware_products", Long.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM hardware_brands", Long.class)).isEqualTo(1);
        mvc.perform(get(BASE + "/" + profileId).with(actor(OWNER))).andExpect(status().isNotFound());
        mvc.perform(delete(BASE + "/" + profileId).with(actor(OWNER)).with(csrf()).param("profileVersion", "1"))
                .andExpect(status().isNotFound());
        mvc.perform(get(BASE + "/statistics").with(actor(OWNER)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalProfiles").value(0))
                .andExpect(jsonPath("$.totalComponentEntries").value(0));
    }

    @Test
    void profileDeletionRequiresAuthenticationCsrfAndNonnegativeVersion() throws Exception {
        mvc.perform(delete(BASE + "/" + profileId).with(csrf()).param("profileVersion", "0"))
                .andExpect(status().isUnauthorized());
        mvc.perform(delete(BASE + "/" + profileId).with(actor(OWNER)).param("profileVersion", "0"))
                .andExpect(status().isForbidden());
        mvc.perform(delete(BASE + "/" + profileId).with(actor(OWNER)).with(csrf()).param("profileVersion", "-1"))
                .andExpect(status().isBadRequest());
        mvc.perform(delete(BASE + "/" + profileId).with(actor(OWNER)).with(csrf()))
                .andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM hardware_profiles", Long.class)).isEqualTo(1);
    }
}
