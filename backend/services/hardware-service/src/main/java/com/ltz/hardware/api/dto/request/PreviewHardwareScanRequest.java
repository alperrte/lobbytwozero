package com.ltz.hardware.api.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;

public record PreviewHardwareScanRequest(@NotNull @AssertTrue Boolean processingConsent,
        @NotNull @Valid HardwareScanRequest scan) { }
