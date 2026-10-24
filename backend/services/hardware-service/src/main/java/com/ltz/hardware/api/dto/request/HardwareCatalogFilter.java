package com.ltz.hardware.api.dto.request;

import com.ltz.hardware.domain.enums.ComponentCategory;
import jakarta.validation.constraints.*;
import java.util.UUID;

public record HardwareCatalogFilter(
        @Size(max = 160) @Pattern(regexp = "[^\\p{Cntrl}<>]*") String q,
        ComponentCategory category,
        UUID brandId,
        @Size(max = 100) @Pattern(regexp = "[^\\p{Cntrl}<>]*") String brand,
        @Min(1) @Max(1048576) Integer minCapacityGb,
        @Min(1) @Max(1048576) Integer maxCapacityGb,
        @Min(1) @Max(20000) Integer minClockMhz,
        @Min(1) @Max(20000) Integer maxClockMhz,
        @Min(1) @Max(1024) Integer minCoreCount,
        @Min(1) @Max(1024) Integer maxCoreCount,
        @Min(1) @Max(10000) Integer minPowerWatts,
        @Min(1) @Max(10000) Integer maxPowerWatts,
        @Size(max = 32) @Pattern(regexp = "[^\\p{Cntrl}<>]+") String memoryType,
        @Size(max = 32) @Pattern(regexp = "[^\\p{Cntrl}<>]+") String socket,
        @Size(max = 32) @Pattern(regexp = "[^\\p{Cntrl}<>]+") String interfaceType,
        @Size(max = 32) @Pattern(regexp = "[^\\p{Cntrl}<>]+") String formFactor) {

    @AssertTrue
    public boolean isRangesValid() {
        return ordered(minCapacityGb, maxCapacityGb) && ordered(minClockMhz, maxClockMhz)
                && ordered(minCoreCount, maxCoreCount) && ordered(minPowerWatts, maxPowerWatts);
    }

    private static boolean ordered(Integer min, Integer max) { return min == null || max == null || min <= max; }
}
