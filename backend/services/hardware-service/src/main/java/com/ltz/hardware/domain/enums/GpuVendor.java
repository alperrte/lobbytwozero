package com.ltz.hardware.domain.enums;

import java.util.List;
import java.util.Locale;

/**
 * Ekran karti cip ureticisi. Kayitli yonga adinin basindan turetilir: kart markasi (ASUS, MSI) ile cip ureticisi
 * farkli kavramlardir ve veri setinde ayri bir cip ureticisi alani yoktur.
 */
public enum GpuVendor {
    NVIDIA("geforce", "nvidia", "quadro", "titan", "tesla", "nvs", "rtx", "gtx", "gt "),
    AMD("radeon", "amd", "firepro", "rx "),
    INTEL("arc", "intel", "iris", "uhd");

    private final List<String> prefixes;

    GpuVendor(String... prefixes) { this.prefixes = List.of(prefixes); }

    /** Kucuk harfli yonga adi onekleri. */
    public List<String> prefixes() { return prefixes; }

    public static List<String> prefixes(List<GpuVendor> vendors) {
        return vendors.stream().flatMap(vendor -> vendor.prefixes.stream()).distinct().toList();
    }

    /** Yonga adinin ureticisi; taninmiyorsa {@code null}. */
    public static GpuVendor of(String chipset) {
        String name = chipset == null ? "" : chipset.strip().toLowerCase(Locale.ROOT);
        for (GpuVendor vendor : values()) {
            if (vendor.prefixes.stream().anyMatch(name::startsWith)) return vendor;
        }
        return null;
    }
}
