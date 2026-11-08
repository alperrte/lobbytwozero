package com.ltz.hardware.api.dto.request;

import com.ltz.hardware.domain.enums.ComponentCategory;
import com.ltz.hardware.domain.enums.GpuVendor;
import jakarta.validation.constraints.*;
import java.util.List;
import java.util.UUID;

/**
 * Katalog filtresi. Liste alanları çoklu seçimdir: değerler kendi içinde VEYA, farklı alanlar VE ile birleşir.
 * Aynı parametre tekrarlanarak gönderilir ({@code socket=AM5&socket=LGA1700}); boş liste filtre uygulamaz.
 */
public record HardwareCatalogFilter(
        @Size(max = 160) @Pattern(regexp = "[^\\p{Cntrl}<>]*") String q,
        ComponentCategory category,
        @Size(max = 50) List<UUID> brandId,
        @Size(max = 100) @Pattern(regexp = "[^\\p{Cntrl}<>]*") String brand,
        @Min(1) @Max(1048576) Integer minCapacityGb,
        @Min(1) @Max(1048576) Integer maxCapacityGb,
        @Min(1) @Max(20000) Integer minClockMhz,
        @Min(1) @Max(20000) Integer maxClockMhz,
        @Min(1) @Max(1024) Integer minCoreCount,
        @Min(1) @Max(1024) Integer maxCoreCount,
        @Min(1) @Max(10000) Integer minPowerWatts,
        @Min(1) @Max(10000) Integer maxPowerWatts,
        @Size(max = 50) List<@Size(max = 32) @Pattern(regexp = "[^\\p{Cntrl}<>]+") String> memoryType,
        @Size(max = 50) List<@Size(max = 32) @Pattern(regexp = "[^\\p{Cntrl}<>]+") String> socket,
        @Size(max = 50) List<@Size(max = 32) @Pattern(regexp = "[^\\p{Cntrl}<>]+") String> interfaceType,
        @Size(max = 50) List<@Size(max = 32) @Pattern(regexp = "[^\\p{Cntrl}<>]+") String> formFactor,
        @Size(max = 50) List<@Size(max = 100) @Pattern(regexp = "[^\\p{Cntrl}<>]+") String> chipset,
        @Size(max = 50) List<@Size(max = 32) @Pattern(regexp = "[^\\p{Cntrl}<>]+") String> storageType,
        @Size(max = 50) List<@Min(1) @Max(1048576) Integer> maximumMemoryGb,
        @Size(max = 50) List<@Min(1) @Max(1024) Integer> memorySlots,
        @Size(max = 50) List<@Min(1) @Max(200) Integer> casLatency,
        @Size(max = 50) List<@Min(1) @Max(1048576) Integer> capacityGb,
        @Size(max = 50) List<@Min(1) @Max(20000) Integer> clockMhz,
        @Size(max = 50) List<@Min(1) @Max(1024) Integer> coreCount,
        /** Ekran kartı çip üreticisi (NVIDIA, AMD, INTEL); kayıtlı yonga adının başından türetilir. */
        @Size(max = 3) List<GpuVendor> gpuVendor,
        /** Yalnız {@code true} filtreler: kayıtlı tümleşik grafiği olan işlemciler. {@code false}/boş filtre uygulamaz. */
        Boolean integratedGraphics) {

    public HardwareCatalogFilter {
        brandId = list(brandId); memoryType = list(memoryType); socket = list(socket); interfaceType = list(interfaceType);
        formFactor = list(formFactor); chipset = list(chipset); storageType = list(storageType);
        maximumMemoryGb = list(maximumMemoryGb); memorySlots = list(memorySlots); casLatency = list(casLatency);
        capacityGb = list(capacityGb); clockMhz = list(clockMhz); coreCount = list(coreCount); gpuVendor = list(gpuVendor);
    }

    @AssertTrue
    public boolean isRangesValid() {
        return ordered(minCapacityGb, maxCapacityGb) && ordered(minClockMhz, maxClockMhz)
                && ordered(minCoreCount, maxCoreCount) && ordered(minPowerWatts, maxPowerWatts);
    }

    private static boolean ordered(Integer min, Integer max) { return min == null || max == null || min <= max; }

    /** Boş/null olmayan, tekrarsız ve değiştirilemez liste; null öğeler atılır. */
    private static <T> List<T> list(List<T> values) {
        return values == null ? List.of() : values.stream().filter(java.util.Objects::nonNull).distinct().toList();
    }
}
