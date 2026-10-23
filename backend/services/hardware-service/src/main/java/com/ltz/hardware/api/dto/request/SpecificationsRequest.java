package com.ltz.hardware.api.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record SpecificationsRequest(
        @Positive @Max(1048576) Integer capacityGb,
        @Positive @Max(20000) Integer clockMhz,
        @Positive @Max(1024) Integer coreCount,
        @Positive @Max(10000) Integer powerWatts,
        @Size(max = 32) @Pattern(regexp = "[^\\p{Cntrl}<>]+") String memoryType,
        @Size(max = 32) @Pattern(regexp = "[^\\p{Cntrl}<>]+") String socket,
        @Size(max = 32) @Pattern(regexp = "[^\\p{Cntrl}<>]+") String interfaceType,
        @Size(max = 32) @Pattern(regexp = "[^\\p{Cntrl}<>]+") String formFactor) { }
