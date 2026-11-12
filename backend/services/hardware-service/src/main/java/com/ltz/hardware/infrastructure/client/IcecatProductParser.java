package com.ltz.hardware.infrastructure.client;

import com.ltz.hardware.application.port.IcecatCatalogPort.Entry;
import com.ltz.hardware.domain.entity.CatalogText;
import com.ltz.hardware.domain.entity.HardwareCatalogContent;
import com.ltz.hardware.domain.entity.HardwareCatalogContent.Asset;
import com.ltz.hardware.domain.entity.HardwareCatalogContent.Feature;
import com.ltz.hardware.domain.entity.HardwareCatalogContent.Group;
import com.ltz.hardware.domain.entity.HardwareCatalogContent.Value;
import com.ltz.hardware.domain.entity.HardwareReviewText;
import com.ltz.hardware.domain.entity.HardwareSpecifications;
import com.ltz.hardware.domain.enums.ComponentCategory;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

/** Only English source feature names are mapped; units remain explicit, unknowns remain null. */
@Component
public class IcecatProductParser {
    public HardwareCatalogContent parse(JsonNode envelope, Entry entry, Map<String, ComponentCategory> taxonomy) {
        if (!"OK".equals(envelope.path("msg").asString())
                || envelope.path("data").path("DemoAccount").asBoolean(false)) throw new IllegalArgumentException("ICECAT_NOT_AUTHORIZED_CONTENT");
        var data = envelope.path("data");
        var general = data.path("GeneralInfo");
        String id = general.path("IcecatId").asString();
        var category = taxonomy.get(general.path("Category").path("CategoryID").asString());
        if (!entry.id().equals(id) || category != entry.category()) throw new IllegalArgumentException("ICECAT_IDENTITY_MISMATCH");
        String model = text(general.path("Title"), 160);
        String brand = text(general.path("Brand"), 100);
        String mpn = optional(general.path("BrandPartCode"), 160);
        var gtins = new ArrayList<String>();
        for (var value : general.path("GTIN")) {
            if (!value.asString().matches("[0-9]{8,14}")) continue;
            if (gtins.size() < 30) gtins.add(value.asString());
        }
        var specs = new LinkedHashMap<String, Value>();
        var groups = groups(data.path("FeaturesGroups"), specs, category);
        var standard = new HardwareSpecifications(new HardwareSpecifications.Values(
                integer(specs, "capacityGb"), integer(specs, "baseClockMhz"), integer(specs, "coreCount"),
                integer(specs, "powerWatts"), string(specs, "memoryType"), string(specs, "socket"),
                string(specs, "interfaceType"), string(specs, "formFactor")));
        return new HardwareCatalogContent(id, category, model, brand, mpn, List.copyOf(gtins), "EN",
                entry.updatedAt(), standard, Map.copyOf(specs), groups, assets(data.path("Gallery"), model),
                HardwareReviewText.hash(data.toString()));
    }

    private List<Group> groups(JsonNode input, Map<String, Value> output, ComponentCategory category) {
        var groups = new ArrayList<Group>();
        for (var group : input) {
            if (groups.size() >= 40) break;
            var features = new ArrayList<Feature>();
            for (var feature : group.path("Features")) {
                if (features.size() >= 100) break;
                var definition = feature.path("Feature");
                String id = optional(definition.path("ID"), 64);
                String name = optional(definition.path("Name").path("Value"), 160);
                String value = optional(feature.path("RawValue"), 500);
                if (value == null) value = optional(feature.path("Value"), 500);
                if (name == null || value == null || id == null) continue;
                String unit = optional(definition.path("Measure").path("Sign"), 32);
                if (unit == null) unit = optional(definition.path("Sign"), 32);
                features.add(new Feature(id, name, value, unit));
                IcecatSpecificationMapping.map(output, category, name, value, unit, id);
            }
            if (!features.isEmpty()) groups.add(new Group(text(group.path("FeatureGroup").path("Name").path("Value"), 160), List.copyOf(features)));
        }
        return List.copyOf(groups);
    }

    private List<Asset> assets(JsonNode gallery, String model) {
        var result = new ArrayList<Asset>();
        for (var image : gallery) {
            if (result.size() >= 4) break;
            String type = image.path("Type").asString();
            if (!"0".equals(image.path("IsPrivate").asString())
                    || !List.of("ProductImage", "PackshotFront-Center", "ProductDetailImage", "ProductPackagingImage").contains(type)) continue;
            String url = optional(image.path("Pic500x500"), 2048);
            String id = optional(image.path("ID"), 64);
            if (url == null || id == null) continue;
            var uri = URI.create(url);
            IcecatHttpClient.requireOfficial(uri);
            result.add(new Asset(id, uri, model, "Y".equals(image.path("IsMain").asString())));
        }
        return List.copyOf(result);
    }
    static String optional(JsonNode value, int limit) {
        if (!value.isValueNode() || value.isNull()) return null;
        String raw = value.asString().replaceAll("<[^>]*>", "").strip();
        return raw.isBlank() ? null : CatalogText.clean(raw, limit);
    }
    static String text(JsonNode value, int limit) {
        String result = optional(value, limit);
        if (result == null) throw new IllegalArgumentException("ICECAT_REQUIRED_FIELD");
        return result;
    }
    private static Integer integer(Map<String, Value> values, String name) {
        var value = values.get(name);
        if (value == null || value.number() == null) return null;
        try { return value.number().intValueExact(); }
        catch (ArithmeticException exception) { return null; }
    }
    private static String string(Map<String, Value> values, String name) {
        var value = values.get(name);
        return value == null || value.text() == null || value.text().length() > 32 ? null : value.text();
    }
}
