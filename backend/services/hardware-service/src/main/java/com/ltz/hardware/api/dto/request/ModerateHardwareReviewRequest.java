package com.ltz.hardware.api.dto.request;

import com.ltz.hardware.domain.enums.HardwareReviewModerationAction;
import jakarta.validation.constraints.*;

public record ModerateHardwareReviewRequest(@NotNull @PositiveOrZero Long reviewVersion,
        @NotNull HardwareReviewModerationAction action, @NotBlank @Size(max = 500) String reason) { }
