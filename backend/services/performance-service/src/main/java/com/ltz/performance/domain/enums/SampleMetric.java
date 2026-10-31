package com.ltz.performance.domain.enums;

/**
 * Desteklenen ölçüm türleri, kanonik birimleri ve makul aralıkları. Birimi istemci seçmez; her tür tek birimdedir.
 * Aralıklar veritabanındaki {@code ck_performance_samples_value} ile aynıdır. Gerçek masaüstü sensör sözleşmesi
 * henüz yoktur; sınırlar fiziksel olarak makul üst sınırlardır ve o sözleşme gelince gözden geçirilmelidir.
 * {@code 0} yalnız sensörün gerçekten 0 raporlayabildiği türlerde (kullanım, bellek, disk, fan) geçerlidir.
 */
public enum SampleMetric {
    CPU_TEMP_C("°C", 0, false, 150),
    GPU_TEMP_C("°C", 0, false, 150),
    CPU_USAGE_PERCENT("%", 0, true, 100),
    GPU_USAGE_PERCENT("%", 0, true, 100),
    RAM_USED_MB("MB", 0, true, 1_048_576),
    VRAM_USED_MB("MB", 0, true, 262_144),
    DISK_READ_MBPS("MB/s", 0, true, 100_000),
    DISK_WRITE_MBPS("MB/s", 0, true, 100_000),
    FAN_RPM("RPM", 0, true, 30_000),
    FPS("FPS", 0, false, 1000);

    private final String unit;
    private final double min;
    private final boolean minInclusive;
    private final double max;

    SampleMetric(String unit, double min, boolean minInclusive, double max) {
        this.unit = unit;
        this.min = min;
        this.minInclusive = minInclusive;
        this.max = max;
    }

    public String unit() { return unit; }

    /** Yalnız kanonik büyük harfli ad kabul edilir (büyük/küçük harf dönüşümü yok); bilinmeyen ad {@code null}. */
    public static SampleMetric parse(String name) {
        for (SampleMetric metric : values()) {
            if (metric.name().equals(name)) return metric;
        }
        return null;
    }

    /** NaN ve sonsuz değerler hiçbir türde kabul edilmez. */
    public boolean accepts(double value) {
        return Double.isFinite(value) && (minInclusive ? value >= min : value > min) && value <= max;
    }
}
