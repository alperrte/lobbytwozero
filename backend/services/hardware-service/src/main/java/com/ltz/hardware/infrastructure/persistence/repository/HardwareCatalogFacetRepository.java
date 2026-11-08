package com.ltz.hardware.infrastructure.persistence.repository;

import com.ltz.hardware.api.dto.response.HardwareCatalogFacetsResponse.Option;
import com.ltz.hardware.domain.enums.ComponentCategory;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Distinct values that actually exist in the active catalog. Column names and JSON keys come from the
 * allowlist below, never from the request; the category is a bound parameter.
 */
@Repository
public class HardwareCatalogFacetRepository {
    static final int MAX_OPTIONS = 500;

    private enum Kind { BASE_NUMBER, BASE_TEXT, TYPED_NUMBER, TYPED_TEXT, BRAND }

    private record Facet(String key, Kind kind, String column) {
        static Facet baseNumber(String key, String column) { return new Facet(key, Kind.BASE_NUMBER, column); }
        static Facet baseText(String key, String column) { return new Facet(key, Kind.BASE_TEXT, column); }
        static Facet typedNumber(String key) { return new Facet(key, Kind.TYPED_NUMBER, null); }
        static Facet typedText(String key) { return new Facet(key, Kind.TYPED_TEXT, null); }
        static Facet brand() { return new Facet("brandId", Kind.BRAND, null); }
    }

    private static final Map<ComponentCategory, List<Facet>> PLAN = Map.of(
            ComponentCategory.CPU, List.of(Facet.brand(), Facet.baseNumber("coreCount", "core_count")),
            ComponentCategory.GPU, List.of(Facet.brand(), Facet.typedText("chipset"),
                    Facet.baseNumber("capacityGb", "capacity_gb")),
            ComponentCategory.MOTHERBOARD, List.of(Facet.brand(), Facet.baseText("socket", "socket"),
                    Facet.baseText("formFactor", "form_factor"), Facet.typedNumber("maximumMemoryGb"),
                    Facet.typedNumber("memorySlots")),
            ComponentCategory.RAM, List.of(Facet.brand(), Facet.baseText("memoryType", "memory_type"),
                    Facet.baseNumber("capacityGb", "capacity_gb"), Facet.baseNumber("clockMhz", "clock_mhz"),
                    Facet.typedNumber("casLatency")),
            ComponentCategory.STORAGE, List.of(Facet.brand(), Facet.typedText("storageType"),
                    Facet.baseNumber("capacityGb", "capacity_gb"), Facet.baseText("interfaceType", "interface_type")));

    private static final String TYPED_SQL = """
            SELECT v, count(*) FROM (
                SELECT jsonb_extract_path_text(s.typed_specs, ?, ?) AS v FROM hardware_product_sources s
                JOIN hardware_products p ON p.id = s.product_id
                WHERE s.quality = 'VERIFIED' AND p.status = 'ACTIVE' AND p.category = ?) t
            WHERE v IS NOT NULL AND btrim(v) <> '' GROUP BY v
            """;

    private final JdbcTemplate jdbc;

    public HardwareCatalogFacetRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public Map<String, List<Option>> facets(ComponentCategory category) {
        var result = new LinkedHashMap<String, List<Option>>();
        for (Facet facet : PLAN.getOrDefault(category, List.of())) {
            result.put(facet.key(), switch (facet.kind()) {
                case BRAND -> brands(category);
                case BASE_NUMBER -> numbers(counts(baseSql(facet.column(), false), category.name()));
                case BASE_TEXT -> texts(counts(baseSql(facet.column(), true), category.name()));
                case TYPED_NUMBER -> numbers(counts(TYPED_SQL, facet.key(), "number", category.name()));
                case TYPED_TEXT -> texts(counts(TYPED_SQL, facet.key(), "text", category.name()));
            });
        }
        if (category == ComponentCategory.GPU) result.put("gpuVendor", vendors(result.getOrDefault("chipset", List.of())));
        return result;
    }

    /** Cip ureticisi yonga adindan turetilir; yalnizca katalogda gercekten bulunan ureticiler listelenir. */
    static List<Option> vendors(List<Option> chipsets) {
        var counts = new java.util.EnumMap<com.ltz.hardware.domain.enums.GpuVendor, Long>(com.ltz.hardware.domain.enums.GpuVendor.class);
        for (Option chipset : chipsets) {
            var vendor = com.ltz.hardware.domain.enums.GpuVendor.of(chipset.value());
            if (vendor != null) counts.merge(vendor, chipset.count(), Long::sum);
        }
        return counts.entrySet().stream().map(e -> new Option(e.getKey().name(), e.getKey().name(), e.getValue())).toList();
    }

    /** {@code column} is always one of the constants in {@link #PLAN}. */
    private static String baseSql(String column, boolean text) {
        return "SELECT " + column + "::text, count(*) FROM hardware_products WHERE category = ? AND status = 'ACTIVE' AND "
                + column + " IS NOT NULL" + (text ? " AND btrim(" + column + ") <> ''" : "") + " GROUP BY 1";
    }

    private Map<String, Long> counts(String sql, Object... arguments) {
        var rows = new LinkedHashMap<String, Long>();
        jdbc.query(sql, rs -> { rows.merge(rs.getString(1), rs.getLong(2), Long::sum); }, arguments);
        return rows;
    }

    private List<Option> brands(ComponentCategory category) {
        return jdbc.query("""
                SELECT b.id, b.name, count(DISTINCT p.id) FROM hardware_products p
                JOIN hardware_product_brands pb ON pb.product_id = p.id
                JOIN hardware_brands b ON b.id = pb.brand_id
                WHERE p.category = ? AND p.status = 'ACTIVE' AND b.status = 'ACTIVE'
                GROUP BY b.id, b.name ORDER BY lower(b.name), b.id LIMIT ?
                """,
                (rs, row) -> new Option(rs.getObject(1, UUID.class).toString(), rs.getString(2), rs.getLong(3)),
                category.name(), MAX_OPTIONS);
    }

    /** JSON may hold 16 and 16.0; both are one option. Sorted numerically ascending. */
    static List<Option> numbers(Map<String, Long> counts) {
        var merged = new TreeMap<BigDecimal, Long>();
        counts.forEach((raw, count) -> {
            try {
                merged.merge(new BigDecimal(raw.strip()).stripTrailingZeros(), count, Long::sum);
            } catch (NumberFormatException ignored) {
                // Not a recorded number: never shown.
            }
        });
        return merged.entrySet().stream().limit(MAX_OPTIONS)
                .map(e -> new Option(e.getKey().toPlainString(), e.getKey().toPlainString(), e.getValue())).toList();
    }

    /** Case variants collapse into the most frequent spelling. Sorted case-insensitively. */
    static List<Option> texts(Map<String, Long> counts) {
        var byKey = new LinkedHashMap<String, List<Map.Entry<String, Long>>>();
        counts.forEach((raw, count) -> byKey.computeIfAbsent(raw.strip().toLowerCase(Locale.ROOT), k -> new ArrayList<>())
                .add(Map.entry(raw.strip(), count)));
        return byKey.values().stream().map(variants -> {
            String label = variants.stream().max(Map.Entry.comparingByValue()).orElseThrow().getKey();
            return new Option(label, label, variants.stream().mapToLong(Map.Entry::getValue).sum());
        }).sorted(Comparator.comparing((Option o) -> o.label().toLowerCase(Locale.ROOT)).thenComparing(Option::label))
                .limit(MAX_OPTIONS).toList();
    }
}
