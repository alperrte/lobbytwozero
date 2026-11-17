package com.ltz.hardware.infrastructure.client;

import static org.assertj.core.api.Assertions.assertThat;

import com.ltz.hardware.domain.entity.HardwareCatalogContent.Value;
import com.ltz.hardware.domain.enums.ComponentCategory;
import java.util.HashMap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class IcecatSpecificationMappingTest {
    @ParameterizedTest
    @CsvSource({
        "CPU,processor base frequency,3.5,GHz,baseClockMhz,3500",
        "CPU,processor base frequency,3500,MHz,baseClockMhz,3500",
        "CPU,processor cache,512,KB,cacheMb,0.512",
        "RAM,memory clock speed,6000,MT/s,transferRateMt,6000",
        "STORAGE,ssd capacity,2,TB,capacityGb,2000",
        "STORAGE,ssd capacity,512,MB,capacityGb,0.512"
    })
    void convertsOnlyRecognizedUnitsWithoutRounding(ComponentCategory category, String feature,
            String raw, String unit, String key, String expected) {
        var output = new HashMap<String, Value>();
        IcecatSpecificationMapping.map(output, category, feature, raw, unit, "test-feature");
        assertThat(output.get(key).number()).isEqualByComparingTo(expected);
    }

    @Test
    void keepsTextAndFirstObservationAndDoesNotGuessUnsupportedValues() {
        var output = new HashMap<String, Value>();
        IcecatSpecificationMapping.map(output, ComponentCategory.MOTHERBOARD, "Processor socket", "AM5", null, "one");
        IcecatSpecificationMapping.map(output, ComponentCategory.MOTHERBOARD, "Processor socket", "AM4", null, "two");
        assertThat(output.get("socket").text()).isEqualTo("AM5");
        IcecatSpecificationMapping.map(output, ComponentCategory.GPU, "GPU clock", "1000", "MHz", "unknown");
        IcecatSpecificationMapping.map(output, ComponentCategory.CPU, "Processor cores", "invalid", "", "invalid");
        IcecatSpecificationMapping.map(output, ComponentCategory.CPU, "Processor cores", "0", "", "zero");
        IcecatSpecificationMapping.map(output, ComponentCategory.CPU, "Processor cores", "1025", "", "large");
        IcecatSpecificationMapping.map(output, ComponentCategory.CPU, "Processor frequency", "3.5", null, "unit");
        assertThat(output).containsOnlyKeys("socket");
    }
}
