package com.ltz.hardware.api.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

public record HardwareScanRequest(@NotNull @Min(1) @Max(1) Integer schemaVersion,
        @NotNull Instant capturedAt,
        @NotNull @Size(min = 1, max = 32) List<@NotNull @Valid ScannedHardwareComponentRequest> components) { }
