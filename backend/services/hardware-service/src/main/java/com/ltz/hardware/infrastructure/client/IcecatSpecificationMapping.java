package com.ltz.hardware.infrastructure.client;

import com.ltz.hardware.domain.entity.HardwareCatalogContent.Value;
import com.ltz.hardware.domain.enums.ComponentCategory;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;
import java.util.Map;

final class IcecatSpecificationMapping {
    private static final String BASE_CLOCK_MHZ = "baseClockMhz";
    private static final String FORM_FACTOR = "formFactor";
    private static final String CAPACITY_GB = "capacityGb";
    private IcecatSpecificationMapping() { }
    private record Field(String key, String unit, int maximum) { }
    private static final Map<String, Field> CPU = Map.ofEntries(
            Map.entry("processor socket", new Field("socket", null, 0)),
            Map.entry("processor cores", new Field("coreCount", "", 1024)),
            Map.entry("processor threads", new Field("threadCount", "", 4096)),
            Map.entry("processor base frequency", new Field(BASE_CLOCK_MHZ, "MHz", 20000)),
            Map.entry("processor frequency", new Field(BASE_CLOCK_MHZ, "MHz", 20000)),
            Map.entry("processor boost frequency", new Field("boostClockMhz", "MHz", 20000)),
            Map.entry("thermal design power (tdp)", new Field("powerWatts", "W", 10000)),
            Map.entry("processor base power", new Field("powerWatts", "W", 10000)),
            Map.entry("processor cache", new Field("cacheMb", "MB", 4096)));
    private static final Map<String, Field> BOARD = Map.of(
            "processor socket", new Field("socket", null, 0),
            "motherboard chipset", new Field("chipset", null, 0),
            "motherboard form factor", new Field(FORM_FACTOR, null, 0),
            "supported memory types", new Field("memoryType", null, 0),
            "number of memory slots", new Field("memorySlots", "", 64),
            "storage drive interfaces", new Field("interfaceType", null, 0));
    private static final Map<String, Field> RAM = Map.of(
            "internal memory", new Field(CAPACITY_GB, "GB", 1048576),
            "internal memory type", new Field("memoryType", null, 0),
            "memory clock speed", new Field(BASE_CLOCK_MHZ, "MHz", 20000),
            "memory data transfer rate", new Field("transferRateMt", "MT/s", 40000),
            "memory form factor", new Field(FORM_FACTOR, null, 0),
            "cas latency", new Field("casLatency", "", 256),
            "number of memory modules", new Field("moduleCount", "", 128));
    private static final Map<String, Field> STORAGE = Map.of(
            "ssd capacity", new Field(CAPACITY_GB, "GB", 1048576),
            "hdd capacity", new Field(CAPACITY_GB, "GB", 1048576),
            "interface", new Field("interfaceType", null, 0),
            "ssd form factor", new Field(FORM_FACTOR, null, 0),
            "hdd size", new Field(FORM_FACTOR, null, 0),
            "read speed", new Field("readMbps", "MB/s", 100000),
            "write speed", new Field("writeMbps", "MB/s", 100000),
            "nvme", new Field("nvme", null, 0),
            "storage drive type", new Field("storageType", null, 0));

    static void map(Map<String, Value> output, ComponentCategory category, String name, String raw, String unit, String id) {
        var fields = switch (category) {
            case CPU -> CPU;
            case MOTHERBOARD -> BOARD;
            case RAM -> RAM;
            case STORAGE -> STORAGE;
            default -> Map.<String, Field>of();
        };
        var field = fields.get(name.toLowerCase(Locale.ROOT));
        if (field == null) return;
        if (field.unit() == null) {
            output.putIfAbsent(field.key(), new Value(null, raw, unit, id));
            return;
        }
        if (category == ComponentCategory.RAM && BASE_CLOCK_MHZ.equals(field.key()) && "MT/s".equals(unit)) {
            field = new Field("transferRateMt", "MT/s", 40000);
        }
        BigDecimal number = number(raw);
        BigDecimal converted = number == null ? null : convert(number, unit, field.unit());
        if (converted != null && converted.signum() > 0 && converted.compareTo(BigDecimal.valueOf(field.maximum())) <= 0) {
            output.putIfAbsent(field.key(), new Value(converted, null, field.unit(), id));
        }
    }

    private static BigDecimal number(String raw) {
        if (!raw.matches("\\d{1,9}(\\.\\d{1,6})?")) return null;
        return new BigDecimal(raw);
    }
    private static BigDecimal convert(BigDecimal number, String unit, String target) {
        String from = unit == null ? "" : unit;
        if (from.equals(target)) return number;
        if ("MHz".equals(target) && "GHz".equals(from)) return number.multiply(BigDecimal.valueOf(1000));
        if ("GB".equals(target) && "TB".equals(from)) return number.multiply(BigDecimal.valueOf(1000));
        if ("GB".equals(target) && "MB".equals(from)) return number.divide(BigDecimal.valueOf(1000), 6, RoundingMode.UNNECESSARY);
        if ("MB".equals(target) && "KB".equals(from)) return number.divide(BigDecimal.valueOf(1000), 6, RoundingMode.UNNECESSARY);
        return null;
    }
}
