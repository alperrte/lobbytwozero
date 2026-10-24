package com.ltz.hardware.api.controller;

import com.ltz.hardware.infrastructure.persistence.PostgresIntegrationSupport;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import static com.ltz.hardware.api.controller.HardwareProfileControllerTest.actor;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"logging.level.root=INFO", "debug=false"})
@AutoConfigureMockMvc
class HardwareCatalogSearchTest extends PostgresIntegrationSupport {
    static final String PATH = "/api/v1/hardware/catalog/products";
    final UUID userId = UUID.randomUUID();
    final UUID maker = UUID.randomUUID();
    final UUID secondMaker = UUID.randomUUID();
    UUID alpha;

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void seed() {
        jdbc.update("DELETE FROM hardware_campaigns");
        jdbc.update("DELETE FROM hardware_profiles");
        jdbc.update("DELETE FROM hardware_product_brands");
        jdbc.update("DELETE FROM hardware_products");
        jdbc.update("DELETE FROM hardware_brands");
        jdbc.update("INSERT INTO hardware_brands(id,name,normalized_name) VALUES (?, 'Maker One','maker one')", maker);
        jdbc.update("INSERT INTO hardware_brands(id,name,normalized_name) VALUES (?, 'Maker Two','maker two')", secondMaker);
        alpha = product("Alpha 100%_!", "GPU", 16, 1500, 8, 180, "DDR5", "AM5", "PCIe", "ATX");
        UUID beta = product("Alpha Pro", "GPU", 8, 1200, 4, 100, "DDR4", "AM4", "PCIe", "mATX");
        product("Unknown", "GPU", null, null, null, null, null, null, null, null);
        product("Alpha Memory", "RAM", 32, 6000, null, null, null, null, null, null);
        UUID archived = product("Alpha Archived", "GPU", 16, null, null, null, null, null, null, null);
        jdbc.update("UPDATE hardware_products SET status='ARCHIVED' WHERE id=?", archived);
        for (UUID id : List.of(alpha, beta)) {
            jdbc.update("INSERT INTO hardware_product_brands(product_id,brand_id) VALUES (?,?)", id, maker);
        }
        jdbc.update("INSERT INTO hardware_product_brands(product_id,brand_id) VALUES (?,?)", alpha, secondMaker);
    }

    UUID product(String name, String category, Integer capacity, Integer clock, Integer cores, Integer power,
            String memory, String socket, String bus, String form) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO hardware_products(id,category,model,normalized_model,capacity_gb,clock_mhz,core_count,
                    power_watts,memory_type,socket,interface_type,form_factor) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)
                """, id, category, name, name.toLowerCase(java.util.Locale.ROOT), capacity, clock, cores, power,
                memory, socket, bus, form);
        return id;
    }

    @Test
    void combinesModelCategoryManufacturerAndTypedSpecificationsWithoutDuplicates() throws Exception {
        mvc.perform(get(PATH).with(actor(userId)).param("q", "aLPHa").param("category", "GPU")
                .param("brand", "maker").param("minCapacityGb", "16").param("maxCapacityGb", "16")
                .param("memoryType", "ddr5"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].id").value(alpha.toString()))
                .andExpect(jsonPath("$.items[0].brands.length()").value(2));
        mvc.perform(get(PATH).with(actor(userId)).param("brand", "maker"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2));
        mvc.perform(get(PATH).with(actor(userId)).param("brandId", maker.toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2));
        mvc.perform(get(PATH).with(actor(userId)).param("brandId", maker.toString()).param("brand", "missing"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
    }

    @ParameterizedTest
    @CsvSource({"minCapacityGb,16,2", "maxCapacityGb,8,1", "minClockMhz,1500,2", "maxClockMhz,1200,1",
            "minCoreCount,8,1", "maxCoreCount,4,1", "minPowerWatts,180,1", "maxPowerWatts,100,1",
            "memoryType,ddr5,1", "socket,am5,1", "interfaceType,pcie,2", "formFactor,atx,1"})
    void filtersOnlyRecordedTechnicalValues(String key, String value, int count) throws Exception {
        mvc.perform(get(PATH).with(actor(userId)).param(key, value)).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(count));
    }

    @ParameterizedTest
    @CsvSource({"minCapacityGb,0", "maxCapacityGb,1048577", "minClockMhz,20001",
            "maxCoreCount,1025", "minPowerWatts,-1", "memoryType,<b>DDR5</b>",
            "category,BAD", "brandId,not-a-uuid", "minCoreCount,no-number"})
    void rejectsInvalidFilters(String key, String value) throws Exception {
        mvc.perform(get(PATH).with(actor(userId)).param(key, value)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void rejectsEveryInvertedRangeAndOverlongSearch() throws Exception {
        for (String stem : List.of("CapacityGb", "ClockMhz", "CoreCount", "PowerWatts")) {
            mvc.perform(get(PATH).with(actor(userId)).param("min" + stem, "2").param("max" + stem, "1"))
                    .andExpect(status().isBadRequest());
        }
        for (var parameter : Map.of("q", 161, "brand", 101, "socket", 33).entrySet()) {
            mvc.perform(get(PATH).with(actor(userId)).param(parameter.getKey(), "x".repeat(parameter.getValue())))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    void escapesSqlLikeMetacharactersAndTreatsInjectionAsLiteralText() throws Exception {
        mvc.perform(get(PATH).with(actor(userId)).param("q", "100%_!"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get(PATH).with(actor(userId)).param("q", "%"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get(PATH).with(actor(userId)).param("q", "' OR 1=1 --"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM hardware_products", Long.class)).isEqualTo(5);
    }

    @Test
    void blankQueryPreservesUnbrandedModelsAndArchivedBrandsCannotMatch() throws Exception {
        mvc.perform(get(PATH).with(actor(userId)).param("q", " ").param("brand", " "))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(4));
        jdbc.update("UPDATE hardware_brands SET status='ARCHIVED' WHERE id=?", maker);
        mvc.perform(get(PATH).with(actor(userId)).param("brandId", maker.toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get(PATH).with(actor(userId)).param("brandId", secondMaker.toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void countAndPaginationShareFiltersAndOrderingHasUuidTieBreak() throws Exception {
        var first = mvc.perform(get(PATH).with(actor(userId)).param("q", "alpha").param("size", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(3)).andExpect(jsonPath("$.items[0].id").value(alpha.toString()))
                .andReturn().getResponse().getContentAsString();
        String repeated = mvc.perform(get(PATH).with(actor(userId)).param("q", "alpha").param("size", "1"))
                .andReturn().getResponse().getContentAsString();
        assertThat(repeated).isEqualTo(first);
        mvc.perform(get(PATH).with(actor(userId)).param("q", "alpha").param("size", "1").param("page", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].model").value("Alpha Memory"));
        mvc.perform(get(PATH).with(actor(userId)).param("q", "alpha").param("page", "5").param("size", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items").isEmpty())
                .andExpect(jsonPath("$.totalElements").value(3));
        mvc.perform(get(PATH).param("q", "alpha")).andExpect(status().isUnauthorized());
    }

    @Test
    void migrationContainsRequiredCategoryAndTechnicalFilterIndexes() {
        List<String> names = jdbc.queryForList("""
                SELECT indexname FROM pg_indexes WHERE tablename='hardware_products'
                """, String.class);
        assertThat(names).contains("ix_hardware_products_status_category_model",
                "ix_hardware_products_active_capacity", "ix_hardware_products_active_clock",
                "ix_hardware_products_active_cores", "ix_hardware_products_active_power");
    }
}
