package com.ltz.hardware.application.mapper;

import com.ltz.hardware.domain.entity.CatalogText;
import com.ltz.hardware.domain.entity.HardwareCatalogContent.Value;
import com.ltz.hardware.domain.entity.HardwareSpecifications;
import com.ltz.hardware.domain.entity.PcPartProduct;
import com.ltz.hardware.domain.enums.ComponentCategory;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public class PcPartMapper {
    private static final Map<ComponentCategory, List<String>> FIELDS = Map.of(
            ComponentCategory.CPU, List.of("core_count", "core_clock", "boost_clock", "microarchitecture", "tdp", "graphics", "smt"),
            ComponentCategory.GPU, List.of("chipset", "memory", "core_clock", "boost_clock", "color", "length"),
            ComponentCategory.MOTHERBOARD, List.of("socket", "form_factor", "max_memory", "memory_slots", "color"),
            ComponentCategory.RAM, List.of("speed", "modules", "color", "first_word_latency", "cas_latency"),
            ComponentCategory.STORAGE, List.of("capacity", "type", "cache", "form_factor", "interface"));
    private static final List<String> MULTI_WORD_BRANDS = List.of(
            "Western Digital", "Silicon Power", "SK Hynix", "Super Talent", "Neo Forza", "Water Panther",
            "Fantom Drives", "Oyen Digital", "Club 3D");
    private final ObjectMapper json;

    public PcPartMapper(ObjectMapper json) { this.json = json; }

    public static boolean allowed(ComponentCategory category, String field) {
        return "name".equals(field) || FIELDS.getOrDefault(category, List.of()).contains(field);
    }

    public PcPartProduct map(ComponentCategory category, JsonNode row) {
        if (!FIELDS.containsKey(category) || !row.isObject()) throw invalid();
        String name = text(row.get("name"), 160);
        if (name == null) throw invalid();
        String brand = MULTI_WORD_BRANDS.stream().filter(b -> name.startsWith(b + " ")).findFirst()
                .orElseGet(() -> name.split(" ", 2)[0]);
        String model = name.length() > brand.length() ? name.substring(brand.length()).strip() : name;
        var technical = new TreeMap<String, Value>();
        switch (category) {
            case CPU -> cpu(row, technical);
            case GPU -> gpu(row, technical);
            case MOTHERBOARD -> motherboard(row, technical);
            case RAM -> memory(row, technical);
            case STORAGE -> storage(row, technical);
            default -> throw invalid();
        }
        var specs = new HardwareSpecifications(new HardwareSpecifications.Values(
                exactInteger(technical, "capacityGb"), exactInteger(technical, "clockMhz"),
                exactInteger(technical, "coreCount"), exactInteger(technical, "powerWatts"),
                textValue(technical, "memoryType"), textValue(technical, "socket"),
                textValue(technical, "interfaceType"), textValue(technical, "formFactor")));
        String content = json.writeValueAsString(technical);
        String identity = digest("pc-part-v1\n" + category.name() + "\n" + CatalogText.normalize(name, 160) + "\n" + content);
        return new PcPartProduct(category, name, model, brand, identity, digest(name + "\n" + content), specs,
                Collections.unmodifiableMap(technical));
    }

    private static void cpu(JsonNode row, Map<String, Value> out) {
        number(row, out, "core_count", "coreCount", "", BigDecimal.ONE);
        number(row, out, "core_clock", "clockMhz", "MHz", BigDecimal.valueOf(1000));
        number(row, out, "boost_clock", "boostClockMhz", "MHz", BigDecimal.valueOf(1000));
        number(row, out, "tdp", "powerWatts", "W", BigDecimal.ONE);
        string(row, out, "microarchitecture", "microarchitecture", "");
        string(row, out, "graphics", "integratedGraphics", "");
        if (row.has("smt")) {
            var smt = row.get("smt");
            if (!smt.isNull() && !smt.isBoolean()) throw invalid();
            out.put("smt", new Value(null, smt.isNull() ? null : smt.asString(), "", "smt"));
        }
    }

    private static void motherboard(JsonNode row, Map<String, Value> out) {
        string(row, out, "socket", "socket", "");
        string(row, out, "form_factor", "formFactor", "");
        number(row, out, "max_memory", "maximumMemoryGb", "GB", BigDecimal.ONE);
        number(row, out, "memory_slots", "memorySlots", "", BigDecimal.ONE);
        string(row, out, "color", "color", "");
    }

    private static void gpu(JsonNode row, Map<String, Value> out) {
        string(row, out, "chipset", "chipset", "");
        number(row, out, "memory", "capacityGb", "GB", BigDecimal.ONE);
        number(row, out, "core_clock", "clockMhz", "MHz", BigDecimal.ONE);
        number(row, out, "boost_clock", "boostClockMhz", "MHz", BigDecimal.ONE);
        number(row, out, "length", "lengthMm", "mm", BigDecimal.ONE);
        string(row, out, "color", "color", "");
    }

    private static void memory(JsonNode row, Map<String, Value> out) {
        JsonNode speed = row.get("speed");
        if (speed != null && speed.isArray()) {
            pair(speed);
            BigDecimal generation = decimal(speed.get(0));
            if (generation != null && (generation.scale() > 0 || generation.intValue() < 1 || generation.intValue() > 5)) throw invalid();
            out.put("memoryType", new Value(null, generation == null ? null : "DDR" + generation.toPlainString(), "", "speed[0]"));
            out.put("clockMhz", new Value(decimal(speed.get(1)), null, "MHz", "speed[1]"));
        } else number(row, out, "speed", "clockMhz", "MHz", BigDecimal.ONE);
        JsonNode modules = row.get("modules");
        if (modules != null && !modules.isNull()) {
            pair(modules);
            BigDecimal count = decimal(modules.get(0));
            BigDecimal size = decimal(modules.get(1));
            out.put("moduleCount", new Value(count, null, "", "modules[0]"));
            out.put("moduleCapacityGb", new Value(size, null, "GB", "modules[1]"));
            out.put("capacityGb", new Value(count == null || size == null ? null : count.multiply(size), null, "GB", "modules"));
        } else if (modules != null) out.put("capacityGb", new Value(null, null, "GB", "modules"));
        number(row, out, "first_word_latency", "firstWordLatencyNs", "ns", BigDecimal.ONE);
        number(row, out, "cas_latency", "casLatency", "", BigDecimal.ONE);
        string(row, out, "color", "color", "");
    }

    private static void storage(JsonNode row, Map<String, Value> out) {
        number(row, out, "capacity", "capacityGb", "GB", BigDecimal.ONE);
        number(row, out, "cache", "cacheMb", "MB", BigDecimal.ONE);
        string(row, out, "interface", "interfaceType", "");
        JsonNode form = row.get("form_factor");
        if (form != null && form.isNumber()) {
            BigDecimal value = decimal(form);
            out.put("formFactor", new Value(value, value.toPlainString(), "in", "form_factor"));
        } else string(row, out, "form_factor", "formFactor", "");
        JsonNode type = row.get("type");
        if (type != null && type.isNumber()) {
            out.put("rotationRpm", new Value(decimal(type), null, "RPM", "type"));
            out.put("storageType", new Value(null, "HDD", "", "type"));
        } else string(row, out, "type", "storageType", "");
    }

    private static void pair(JsonNode value) {
        if (!value.isArray() || value.size() != 2) throw invalid();
    }

    private static void number(JsonNode row, Map<String, Value> out, String field, String key, String unit, BigDecimal factor) {
        if (!row.has(field)) return;
        BigDecimal value = decimal(row.get(field));
        out.put(key, new Value(value == null ? null : value.multiply(factor).stripTrailingZeros(), null, unit, field));
    }

    private static void string(JsonNode row, Map<String, Value> out, String field, String key, String unit) {
        if (row.has(field)) out.put(key, new Value(null, text(row.get(field), 160), unit, field));
    }

    private static BigDecimal decimal(JsonNode node) {
        if (node == null || node.isNull()) return null;
        if (!node.isNumber()) throw invalid();
        BigDecimal value = node.decimalValue().stripTrailingZeros();
        if (value.signum() < 0 || value.compareTo(BigDecimal.valueOf(1048576)) > 0) throw invalid();
        return value;
    }

    private static String text(JsonNode node, int max) {
        if (node == null || node.isNull()) return null;
        if (!node.isString()) throw invalid();
        return CatalogText.clean(node.asString(), max);
    }

    private static Integer exactInteger(Map<String, Value> values, String key) {
        Value value = values.get(key);
        if (value == null || value.number() == null || value.number().signum() == 0) return null;
        try { return value.number().intValueExact(); }
        catch (ArithmeticException ignored) { return null; } // Fractional GB stays exact in typed specifications.
    }

    private static String textValue(Map<String, Value> values, String key) {
        Value value = values.get(key);
        return value == null ? null : value.text();
    }

    public static String digest(String text) { return digest(text.getBytes(StandardCharsets.UTF_8)); }

    public static String digest(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (NoSuchAlgorithmException exception) { throw new IllegalStateException("SHA-256 kullanılamıyor", exception); }
    }

    private static IllegalArgumentException invalid() { return new IllegalArgumentException("INVALID_TECHNICAL_ROW"); }
}
