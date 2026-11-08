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
    static final String FACETS = "/api/v1/hardware/catalog/facets";
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
    void searchMatchesWordPrefixesAcrossTokensAndBrandNames() throws Exception {
        UUID amd = UUID.randomUUID();
        jdbc.update("INSERT INTO hardware_brands(id,name,normalized_name) VALUES (?, 'AMD','amd')", amd);
        UUID x3d = product("Ryzen 7 9800X3D", "CPU", null, null, 8, 120, null, "AM5", null, null);
        UUID plain = product("Ryzen 5 7600", "CPU", null, null, 6, 65, null, "AM5", null, null);
        UUID core = product("Core i7-14700K", "CPU", null, null, 20, 125, null, "LGA1700", null, null);
        for (UUID id : List.of(x3d, plain)) {
            jdbc.update("INSERT INTO hardware_product_brands(product_id,brand_id) VALUES (?,?)", id, amd);
        }
        assertThat(search("r")).containsExactlyInAnyOrder(x3d, plain);
        assertThat(search("ry")).containsExactlyInAnyOrder(x3d, plain);
        assertThat(search("Ryzen")).containsExactlyInAnyOrder(x3d, plain);
        assertThat(search("ryzen 98")).containsExactly(x3d);
        assertThat(search("RYZEN   9800")).containsExactly(x3d);
        assertThat(search("ryzen 7")).containsExactlyInAnyOrder(x3d, plain);
        assertThat(search("amd ryzen")).containsExactlyInAnyOrder(x3d, plain);
        assertThat(search("amd 98")).containsExactly(x3d);
        assertThat(search("x3d")).containsExactly(x3d);
        assertThat(search("i7")).containsExactly(core);
        assertThat(search("14700")).containsExactly(core);
        assertThat(search("intel ryzen")).isEmpty();
        // A short token only matches the start of a word, so "9" does not hit the middle of 9800X3D or 7600.
        assertThat(search("ryzen 8")).isEmpty();
        jdbc.update("UPDATE hardware_brands SET status='ARCHIVED' WHERE id=?", amd);
        assertThat(search("amd")).isEmpty();
    }

    List<UUID> search(String q) throws Exception {
        String body = mvc.perform(get(PATH).with(actor(userId)).param("q", q).param("category", "CPU").param("size", "50"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return com.jayway.jsonpath.JsonPath.<List<String>>read(body, "$.items[*].id").stream().map(UUID::fromString).toList();
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

    void source(UUID productId, String category, String typedSpecs) {
        String key = UUID.randomUUID().toString();
        String hash = (key + key).replace("-", "").substring(0, 64);
        jdbc.update("""
                INSERT INTO hardware_product_sources(id,product_id,source_provider,external_product_id,category,brand,
                    locale,synced_at,quality,content_hash,typed_specs)
                VALUES (?,?,'PC_PART_DATASET',?,?,'Maker One','en',now(),'VERIFIED',?,CAST(? AS jsonb))
                """, UUID.randomUUID(), productId, key, category, hash, typedSpecs);
    }

    @Test
    void filtersOnTypedSourceSpecificationsExactlyAndCombinesThemWithOtherFilters() throws Exception {
        UUID card = product("Typed Card", "GPU", 24, null, null, null, null, null, null, null);
        source(card, "GPU", "{\"chipset\":{\"text\":\"Radeon RX 7900 XTX\",\"number\":null,\"unit\":\"\"}}");
        UUID board = product("Typed Board", "MOTHERBOARD", null, null, null, null, null, "AM5", null, "ATX");
        source(board, "MOTHERBOARD", "{\"maximumMemoryGb\":{\"number\":192.0,\"unit\":\"GB\"},\"memorySlots\":{\"number\":4}}");
        UUID memory = product("Typed Memory", "RAM", 32, 6000, null, null, "DDR5", null, null, null);
        source(memory, "RAM", "{\"casLatency\":{\"number\":30}}");
        UUID drive = product("Typed Drive", "STORAGE", 1000, null, null, null, null, null, "M.2 PCIe 4.0 X4", "M.2-2280");
        source(drive, "STORAGE", "{\"storageType\":{\"text\":\"SSD\"}}");
        // Quarantined source rows never match: only VERIFIED content is searchable.
        UUID hidden = product("Typed Hidden", "STORAGE", 1000, null, null, null, null, null, null, null);
        source(hidden, "STORAGE", "{\"storageType\":{\"text\":\"SSD\"}}");
        jdbc.update("UPDATE hardware_product_sources SET quality='QUARANTINED' WHERE product_id=?", hidden);

        assertTyped(card, "chipset", "radeon rx 7900 xtx");
        assertTyped(board, "maximumMemoryGb", "192");
        assertTyped(board, "memorySlots", "4");
        assertTyped(memory, "casLatency", "30");
        assertTyped(drive, "storageType", "ssd");
        mvc.perform(get(PATH).with(actor(userId)).param("category", "RAM").param("casLatency", "30")
                .param("memoryType", "DDR5").param("minCapacityGb", "32").param("maxCapacityGb", "32"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get(PATH).with(actor(userId)).param("category", "RAM").param("casLatency", "31"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get(PATH).with(actor(userId)).param("chipset", "Radeon").param("category", "GPU"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void integratedGraphicsFilterReturnsOnlyCpusWithRecordedGraphics() throws Exception {
        UUID withGraphics = product("Igpu Yes", "CPU", null, null, 8, null, null, null, null, null);
        source(withGraphics, "CPU", "{\"integratedGraphics\":{\"text\":\"Radeon Graphics\",\"number\":null,\"unit\":\"\"}}");
        UUID blank = product("Igpu Blank", "CPU", null, null, 8, null, null, null, null, null);
        source(blank, "CPU", "{\"integratedGraphics\":{\"text\":\"  \",\"number\":null,\"unit\":\"\"}}");
        UUID falseText = product("Igpu False", "CPU", null, null, 8, null, null, null, null, null);
        source(falseText, "CPU", "{\"integratedGraphics\":{\"text\":\"false\"}}");
        UUID missing = product("Igpu Missing", "CPU", null, null, 8, null, null, null, null, null);
        source(missing, "CPU", "{\"microarchitecture\":{\"text\":\"Zen 4\"}}");
        UUID quarantined = product("Igpu Quarantined", "CPU", null, null, 8, null, null, null, null, null);
        source(quarantined, "CPU", "{\"integratedGraphics\":{\"text\":\"Radeon Graphics\"}}");
        jdbc.update("UPDATE hardware_product_sources SET quality='QUARANTINED' WHERE product_id=?", quarantined);

        mvc.perform(get(PATH).with(actor(userId)).param("category", "CPU").param("integratedGraphics", "true"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].id").value(withGraphics.toString()));
        mvc.perform(get(PATH).with(actor(userId)).param("category", "CPU").param("integratedGraphics", "true")
                .param("minCoreCount", "9")).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
        // Unchecked box sends nothing; an explicit false is not a filter either.
        mvc.perform(get(PATH).with(actor(userId)).param("category", "CPU").param("integratedGraphics", "false"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(5));
        mvc.perform(get(PATH).with(actor(userId)).param("category", "CPU"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(5));
        mvc.perform(get(PATH).with(actor(userId)).param("integratedGraphics", "maybe")).andExpect(status().isBadRequest());
    }

    void assertTyped(UUID expected, String key, String value) throws Exception {
        mvc.perform(get(PATH).with(actor(userId)).param(key, value)).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].id").value(expected.toString()));
    }

    @ParameterizedTest
    @CsvSource({"casLatency,0", "casLatency,201", "memorySlots,0", "memorySlots,1025", "maximumMemoryGb,0",
            "chipset,<b>x</b>", "storageType,<i>"})
    void rejectsInvalidTypedSourceFilters(String key, String value) throws Exception {
        mvc.perform(get(PATH).with(actor(userId)).param(key, value)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void repeatedParametersMatchAnyOfTheValuesAndCombineAcrossFieldsWithAnd() throws Exception {
        UUID am5 = product("Multi Board A", "MOTHERBOARD", null, null, null, null, null, "AM5", null, "ATX");
        UUID am4 = product("Multi Board B", "MOTHERBOARD", null, null, null, null, null, "AM4", null, "mATX");
        product("Multi Board C", "MOTHERBOARD", null, null, null, null, null, "LGA1700", null, "ATX");
        source(am5, "MOTHERBOARD", "{\"memorySlots\":{\"number\":4}}");
        source(am4, "MOTHERBOARD", "{\"memorySlots\":{\"number\":2}}");
        mvc.perform(get(PATH).with(actor(userId)).param("category", "MOTHERBOARD").param("socket", "AM5", "am4"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2));
        mvc.perform(get(PATH).with(actor(userId)).param("category", "MOTHERBOARD").param("socket", "AM5", "AM4")
                .param("formFactor", "ATX")).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get(PATH).with(actor(userId)).param("category", "MOTHERBOARD").param("memorySlots", "2", "4"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2));
        mvc.perform(get(PATH).with(actor(userId)).param("category", "MOTHERBOARD").param("memorySlots", "2"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void repeatedBrandsCapacitiesCoresAndClocksAreAlternatives() throws Exception {
        mvc.perform(get(PATH).with(actor(userId)).param("category", "GPU")
                .param("brandId", maker.toString(), secondMaker.toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2));
        mvc.perform(get(PATH).with(actor(userId)).param("category", "GPU").param("capacityGb", "8", "16"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2));
        mvc.perform(get(PATH).with(actor(userId)).param("category", "GPU").param("capacityGb", "8", "16")
                .param("coreCount", "4"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get(PATH).with(actor(userId)).param("category", "RAM").param("clockMhz", "6000", "5600"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get(PATH).with(actor(userId)).param("category", "GPU").param("capacityGb", "0"))
                .andExpect(status().isBadRequest());
        mvc.perform(get(PATH).with(actor(userId)).param("brandId", maker.toString(), "not-a-uuid"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void chipsetValuesContainingCommasStayOneValue() throws Exception {
        UUID card = product("Comma Card", "GPU", 8, null, null, null, null, null, null, null);
        source(card, "GPU", "{\"chipset\":{\"text\":\"GeForce RTX 4060, Founders\"}}");
        mvc.perform(get(PATH).with(actor(userId)).param("category", "GPU").param("chipset", "GeForce RTX 4060, Founders"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void gpuVendorFiltersByChipsetPrefixAndCombinesAsAlternatives() throws Exception {
        UUID nvidia = product("Vendor Nvidia", "GPU", 12, null, null, null, null, null, null, null);
        source(nvidia, "GPU", "{\"chipset\":{\"text\":\"GeForce RTX 4070\"}}");
        UUID amd = product("Vendor Amd", "GPU", 16, null, null, null, null, null, null, null);
        source(amd, "GPU", "{\"chipset\":{\"text\":\"Radeon RX 7800 XT\"}}");
        UUID intel = product("Vendor Intel", "GPU", 16, null, null, null, null, null, null, null);
        source(intel, "GPU", "{\"chipset\":{\"text\":\"Arc A770\"}}");
        mvc.perform(get(PATH).with(actor(userId)).param("category", "GPU").param("gpuVendor", "NVIDIA"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].id").value(nvidia.toString()));
        mvc.perform(get(PATH).with(actor(userId)).param("category", "GPU").param("gpuVendor", "AMD", "INTEL"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2));
        mvc.perform(get(PATH).with(actor(userId)).param("category", "GPU").param("gpuVendor", "AMD", "INTEL")
                .param("capacityGb", "16").param("chipset", "Arc A770"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].id").value(intel.toString()));
        mvc.perform(get(PATH).with(actor(userId)).param("gpuVendor", "MATROX")).andExpect(status().isBadRequest());
        mvc.perform(get(FACETS).with(actor(userId)).param("category", "GPU"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.facets.gpuVendor.length()").value(3))
                .andExpect(jsonPath("$.facets.gpuVendor[0].value").value("NVIDIA"))
                .andExpect(jsonPath("$.facets.gpuVendor[1].value").value("AMD"))
                .andExpect(jsonPath("$.facets.gpuVendor[2].value").value("INTEL"));
    }

    @Test
    void facetsListOnlyRecordedActiveValuesPerCategory() throws Exception {
        UUID card = product("Facet Card", "GPU", 12, null, null, null, null, null, null, null);
        source(card, "GPU", "{\"chipset\":{\"text\":\"GeForce RTX 4070\"}}");
        UUID twin = product("Facet Twin", "GPU", 12, null, null, null, null, null, null, null);
        source(twin, "GPU", "{\"chipset\":{\"text\":\"geforce rtx 4070\"}}");
        UUID board = product("Facet Board", "MOTHERBOARD", null, null, null, null, null, "AM5", null, "ATX");
        source(board, "MOTHERBOARD", "{\"maximumMemoryGb\":{\"number\":192.0},\"memorySlots\":{\"number\":4}}");
        UUID board2 = product("Facet Board 2", "MOTHERBOARD", null, null, null, null, null, "AM5", null, "Mini ITX");
        source(board2, "MOTHERBOARD", "{\"maximumMemoryGb\":{\"number\":96},\"memorySlots\":{\"number\":2}}");
        jdbc.update("INSERT INTO hardware_product_brands(product_id,brand_id) VALUES (?,?)", card, maker);

        mvc.perform(get(FACETS).with(actor(userId)).param("category", "GPU"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.category").value("GPU"))
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("private")))
                .andExpect(jsonPath("$.facets.capacityGb.length()").value(3))
                .andExpect(jsonPath("$.facets.capacityGb[0].value").value("8"))
                .andExpect(jsonPath("$.facets.capacityGb[2].value").value("16"))
                .andExpect(jsonPath("$.facets.chipset.length()").value(1))
                .andExpect(jsonPath("$.facets.chipset[0].count").value(2))
                .andExpect(jsonPath("$.facets.brandId[0].value").value(maker.toString()))
                .andExpect(jsonPath("$.facets.brandId[0].label").value("Maker One"));
        mvc.perform(get(FACETS).with(actor(userId)).param("category", "MOTHERBOARD"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.facets.maximumMemoryGb[0].value").value("96"))
                .andExpect(jsonPath("$.facets.maximumMemoryGb[1].value").value("192"))
                .andExpect(jsonPath("$.facets.memorySlots.length()").value(2))
                .andExpect(jsonPath("$.facets.socket[0].value").value("AM5"))
                .andExpect(jsonPath("$.facets.formFactor.length()").value(2));
        mvc.perform(get(FACETS).with(actor(userId)).param("category", "COOLING"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.facets").isEmpty());
        mvc.perform(get(FACETS).with(actor(userId)).param("category", "BAD")).andExpect(status().isBadRequest());
        mvc.perform(get(FACETS).with(actor(userId))).andExpect(status().isBadRequest());
        mvc.perform(get(FACETS).param("category", "GPU")).andExpect(status().isUnauthorized());
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
