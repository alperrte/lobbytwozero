package com.ltz.hardware.api.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

public record PreviewHardwareScanRequest(@NotNull @AssertTrue Boolean processingConsent,
        @NotNull @Valid HardwareScanRequest scan) { }
