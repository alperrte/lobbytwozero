package com.ltz.hardware.api.dto.request;

import com.ltz.hardware.domain.enums.HardwareReviewReportReason;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateHardwareReviewReportRequest(@NotNull HardwareReviewReportReason reason,
        @Size(min = 1, max = 500) String details) { }
