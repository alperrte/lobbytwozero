package com.ltz.hardware.infrastructure.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ltz.hardware.application.port.IcecatCatalogPort.Entry;
import com.ltz.hardware.domain.enums.ComponentCategory;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

class IcecatProductParserTest {
    private final ObjectMapper json = new ObjectMapper();
    private final IcecatProductParser parser = new IcecatProductParser();
    private final Entry entry = new Entry("123", ComponentCategory.CPU, Instant.EPOCH, false, 1);
    private final Map<String, ComponentCategory> taxonomy = Map.of("cpu", ComponentCategory.CPU);

    private ObjectNode envelope() {
        return (ObjectNode) json.readTree("""
            {"msg":"OK","data":{"GeneralInfo":{"IcecatId":"123","Category":{"CategoryID":"cpu"},
            "Title":"Test CPU","Brand":"Test Brand","BrandPartCode":"TEST-1","GTIN":["12345678","invalid"]},
            "FeaturesGroups":[{"FeatureGroup":{"Name":{"Value":"Processor"}},"Features":[
              {"Feature":{"ID":"frequency","Name":{"Value":"Processor base frequency"},"Measure":{"Sign":"GHz"}},"RawValue":"3.5"},
              {"Feature":{"ID":"cores","Name":{"Value":"Processor cores"},"Sign":""},"Value":"8"},
              {"Feature":{"ID":"fraction","Name":{"Value":"Processor threads"}},"RawValue":"8.5"},
              {"Feature":{"ID":"socket","Name":{"Value":"Processor socket"}},"RawValue":"AM5"},
              {"Feature":{"ID":"bad","Name":{"Value":"Processor cache"}},"RawValue":"unknown"},
              {"Feature":{"ID":"unnamed"},"RawValue":"ignored"}]}],
            "Gallery":[{"Type":"ProductImage","IsPrivate":"1"},
              {"Type":"ProductImage","IsPrivate":"0","ID":"empty"},
              {"Type":"Other","IsPrivate":"0"}]}}
            """);
    }

    @Test
    void mapsExplicitSourceUnitsAndRejectsIncompleteFeaturesWithoutInventingValues() {
        var content = parser.parse(envelope(), entry, taxonomy);
        assertThat(content.gtins()).containsExactly("12345678");
        assertThat(content.specifications().getClockMhz()).isEqualTo(3500);
        assertThat(content.specifications().getCoreCount()).isEqualTo(8);
        assertThat(content.specifications().getSocket()).isEqualTo("AM5");
        assertThat(content.featureGroups()).hasSize(1);
        assertThat(content.featureGroups().getFirst().features()).hasSize(5);
        assertThat(content.typedSpecs()).doesNotContainKeys("cacheMb", "capacityGb");
        assertThat(content.assets()).isEmpty();
        assertThat(content.contentHash()).hasSize(64);
    }

    @Test
    void failsClosedForDemoUnauthorizedIdentityAndMissingRequiredText() {
        var denied = envelope();
        denied.put("msg", "DENIED");
        assertThatThrownBy(() -> parser.parse(denied, entry, taxonomy)).isInstanceOf(IllegalArgumentException.class);
        var demo = envelope();
        ((ObjectNode) demo.path("data")).put("DemoAccount", true);
        assertThatThrownBy(() -> parser.parse(demo, entry, taxonomy)).isInstanceOf(IllegalArgumentException.class);
        var identity = envelope();
        Map<String, ComponentCategory> missingTaxonomy = Map.of();
        assertThatThrownBy(() -> parser.parse(identity, entry, missingTaxonomy)).isInstanceOf(IllegalArgumentException.class);
        var missing = envelope();
        ((ObjectNode) missing.path("data").path("GeneralInfo")).putNull("Title");
        assertThatThrownBy(() -> parser.parse(missing, entry, taxonomy)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void limitsGroupsAndFeaturesAndDropsEmptyGroups() {
        var input = envelope();
        var data = (ObjectNode) input.path("data");
        var group = ((ObjectNode) data.path("FeaturesGroups").get(0)).deepCopy();
        var feature = group.path("Features").get(0).deepCopy();
        var features = group.putArray("Features");
        for (int index = 0; index < 101; index++) features.add(feature);
        var groups = data.putArray("FeaturesGroups");
        groups.add(json.createObjectNode());
        for (int index = 0; index < 41; index++) groups.add(group);
        var content = parser.parse(input, entry, taxonomy);
        assertThat(content.featureGroups()).hasSize(40).allSatisfy(value -> assertThat(value.features()).hasSize(100));
    }

    @Test
    void keepsTextOptionalAndNormalizesProviderMarkup() {
        assertThat(IcecatProductParser.optional(json.nullNode(), 100)).isNull();
        assertThat(IcecatProductParser.optional(json.createObjectNode(), 100)).isNull();
        assertThat(IcecatProductParser.optional(json.readTree("\"  <b> </b> \""), 100)).isNull();
        assertThat(IcecatProductParser.text(json.readTree("\" <b>AM5</b> \""), 100)).isEqualTo("AM5");
    }
}
