package com.ltz.hardware.api.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.UUID;

public record ApplyHardwareSyncRequest(@NotNull @PositiveOrZero Long profileVersion,
        @NotNull UUID consentGeneration, @NotNull @Positive Long sequence,
        @NotNull @Valid HardwareScanRequest scan) { }
