package com.ltz.hardware.application.mapper;

import com.ltz.hardware.domain.enums.ComponentCategory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import tools.jackson.databind.ObjectMapper;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PcPartMapperTest {
    private final ObjectMapper json = new ObjectMapper();
    private final PcPartMapper mapper = new PcPartMapper(json);

    @Test
    void convertsCpuGhzAndPreservesNullAndOptionalSmtWithoutInventingSocket() {
        var row = mapper.map(ComponentCategory.CPU, json.readTree("""
            {"name":"AMD Ryzen Test","core_count":8,"core_clock":3.95,"boost_clock":null,
             "tdp":65,"microarchitecture":"Zen Test","graphics":null,"smt":true}
            """));
        assertThat(row.brand()).isEqualTo("AMD");
        assertThat(row.model()).isEqualTo("Ryzen Test");
        assertThat(row.specifications().getClockMhz()).isEqualTo(3950);
        assertThat(row.specifications().getCoreCount()).isEqualTo(8);
        assertThat(row.specifications().getSocket()).isNull();
        assertThat(row.technical().get("boostClockMhz").number()).isNull();
        assertThat(row.technical().get("integratedGraphics").text()).isNull();
        assertThat(row.technical().get("smt").text()).isEqualTo("true");
    }

    @Test
    void mapsMotherboardMaximumMemoryAndSlotsSeparatelyFromCapacity() {
        var row = mapper.map(ComponentCategory.MOTHERBOARD, json.readTree("""
            {"name":"Asus Test","socket":"AM5","form_factor":"ATX","max_memory":192,"memory_slots":4,"color":"Black"}
            """));
        assertThat(row.specifications().getSocket()).isEqualTo("AM5");
        assertThat(row.specifications().getCapacityGb()).isNull();
        assertThat(row.technical().get("maximumMemoryGb").number()).isEqualByComparingTo("192");
        assertThat(row.technical().get("memorySlots").number()).isEqualByComparingTo("4");
    }

    @Test
    void mapsMemoryKitAndFractionalLatency() {
        var row = mapper.map(ComponentCategory.RAM, json.readTree("""
            {"name":"Silicon Power Test 32 GB","speed":[5,6000],"modules":[2,16],
             "first_word_latency":12.345,"cas_latency":2.5,"color":null}
            """));
        assertThat(row.brand()).isEqualTo("Silicon Power");
        assertThat(row.specifications().getCapacityGb()).isEqualTo(32);
        assertThat(row.specifications().getClockMhz()).isEqualTo(6000);
        assertThat(row.specifications().getMemoryType()).isEqualTo("DDR5");
        assertThat(row.technical().get("moduleCount").number()).isEqualByComparingTo("2");
        assertThat(row.technical().get("casLatency").number()).isEqualByComparingTo("2.5");
    }

    @Test
    void scalarMemorySpeedDoesNotInventDdrGeneration() {
        var row = mapper.map(ComponentCategory.RAM, json.readTree("{\"name\":\"Mushkin Test\",\"speed\":333,\"modules\":null}"));
        assertThat(row.specifications().getClockMhz()).isEqualTo(333);
        assertThat(row.specifications().getMemoryType()).isNull();
        assertThat(row.specifications().getCapacityGb()).isNull();
    }

    @Test
    void mapsGpuMemoryMhzChipsetLengthAndNullWithoutInventingPowerOrMemoryType() {
        var row = mapper.map(ComponentCategory.GPU, json.readTree("""
            {"name":"Club 3D Radeon Test","chipset":"Radeon Test","memory":0.125,
             "core_clock":1320,"boost_clock":null,"length":235.5,"color":null,
             "price":"DO_NOT_PERSIST","store":"DO_NOT_PERSIST"}
            """));
        assertThat(row.brand()).isEqualTo("Club 3D");
        assertThat(row.model()).isEqualTo("Radeon Test");
        assertThat(row.specifications().getClockMhz()).isEqualTo(1320);
        assertThat(row.specifications().getCapacityGb()).isNull();
        assertThat(row.specifications().getPowerWatts()).isNull();
        assertThat(row.specifications().getMemoryType()).isNull();
        assertThat(row.technical().get("capacityGb").number()).isEqualByComparingTo("0.125");
        assertThat(row.technical().get("boostClockMhz").number()).isNull();
        assertThat(row.technical().get("lengthMm").number()).isEqualByComparingTo("235.5");
        assertThat(row.technical().get("chipset").text()).isEqualTo("Radeon Test");
        assertThat(json.writeValueAsString(row)).doesNotContain("DO_NOT_PERSIST", "price", "store");
    }

    @Test
    void gpuIdentityPreservesMemoryManufacturerAndColorVariantsAndExcludesPrices() {
        String base = "{\"name\":\"MSI GeForce Test\",\"chipset\":\"GeForce Test\",\"memory\":8,\"color\":\"Black\"}";
        var product = mapper.map(ComponentCategory.GPU, json.readTree(base));
        for (String variant : java.util.List.of(base.replace("8", "16"), base.replace("Black", "White"), base.replace("MSI", "Asus"))) {
            assertThat(mapper.map(ComponentCategory.GPU, json.readTree(variant)).identity()).isNotEqualTo(product.identity());
        }
        var priced = json.readTree(base).deepCopy();
        ((tools.jackson.databind.node.ObjectNode) priced).put("price", "DO_NOT_PERSIST");
        assertThat(mapper.map(ComponentCategory.GPU, priced).identity()).isEqualTo(product.identity());
    }

    @Test
    void preservesFractionalGbAndHddRpmAndNumericInchesWithoutRounding() {
        var row = mapper.map(ComponentCategory.STORAGE, json.readTree("""
            {"name":"Western Digital Test","capacity":73.4,"type":15000,"cache":null,"form_factor":2.5,"interface":"SAS"}
            """));
        assertThat(row.brand()).isEqualTo("Western Digital");
        assertThat(row.specifications().getCapacityGb()).isNull();
        assertThat(row.technical().get("capacityGb").number()).isEqualByComparingTo("73.4");
        assertThat(row.technical().get("rotationRpm").number()).isEqualByComparingTo("15000");
        assertThat(row.technical().get("storageType").text()).isEqualTo("HDD");
        assertThat(row.technical().get("formFactor").unit()).isEqualTo("in");
        assertThat(row.specifications().getFormFactor()).isEqualTo("2.5");
    }

    @Test
    void pricesUnknownFieldsAndKeyOrderCannotAffectIdentityOrPersistedContent() {
        var left = mapper.map(ComponentCategory.STORAGE, json.readTree("""
            {"name":"Samsung 990 Pro","capacity":1000,"type":"SSD","price":"DO_NOT_PERSIST","store":"DO_NOT_PERSIST"}
            """));
        var right = mapper.map(ComponentCategory.STORAGE, json.readTree("""
            {"type":"SSD","capacity":1000.0,"name":"Samsung 990 Pro","price_per_gb":"DIFFERENT"}
            """));
        assertThat(left.identity()).isEqualTo(right.identity());
        assertThat(left.contentHash()).isEqualTo(right.contentHash());
        assertThat(json.writeValueAsString(left)).doesNotContain("DO_NOT_PERSIST", "price", "store");
        var variant = mapper.map(ComponentCategory.STORAGE, json.readTree("{\"name\":\"Samsung 990 Pro\",\"capacity\":2000,\"type\":\"SSD\"}"));
        assertThat(left.identity()).isNotEqualTo(variant.identity());
        assertThat(PcPartMapper.allowed(ComponentCategory.GPU, "price")).isFalse();
    }

    @ParameterizedTest
    @CsvSource({"CPU,{}", "CPU,[]", "GPU,{}", "CPU,{\"name\":1}", "CPU,{\"name\":\"\"}",
            "CPU,'{\"name\":\"AMD Test\",\"tdp\":-1}'", "CPU,'{\"name\":\"AMD Test\",\"tdp\":\"65\"}'",
            "CPU,'{\"name\":\"AMD Test\",\"smt\":2}'", "CPU,'{\"name\":\"AMD Test\",\"graphics\":1}'",
            "CPU,'{\"name\":\"AMD Test\",\"core_count\":1048577}'",
            "RAM,'{\"name\":\"Corsair Test\",\"speed\":[6,6000]}'",
            "RAM,'{\"name\":\"Corsair Test\",\"modules\":[1]}'"})
    void rejectsInvalidTechnicalValues(ComponentCategory category, String row) {
        var input = json.readTree(row);
        assertThatThrownBy(() -> mapper.map(category, input)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void optionalNullAndMissingValuesStayDistinctAndZeroIsNotFabricatedIntoCommonColumns() {
        var row = mapper.map(ComponentCategory.RAM, json.readTree("{\"name\":\"Test\",\"speed\":[null,null],\"modules\":[null,0]}"));
        assertThat(row.specifications().getCapacityGb()).isNull();
        assertThat(row.technical()).doesNotContainKey("color");
        assertThat(row.technical().get("memoryType").text()).isNull();
        assertThat(row.technical().get("moduleCapacityGb").number()).isEqualByComparingTo("0");
        var cpu = mapper.map(ComponentCategory.CPU, json.readTree("{\"name\":\"Test\",\"tdp\":0,\"smt\":null}"));
        assertThat(cpu.specifications().getPowerWatts()).isNull();
    }
}
