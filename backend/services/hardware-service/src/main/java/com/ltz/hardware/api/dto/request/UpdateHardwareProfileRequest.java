package com.ltz.hardware.api.dto.request;

import com.ltz.hardware.domain.enums.DeviceType;
import jakarta.validation.constraints.*;

public record UpdateHardwareProfileRequest(
        @NotNull @PositiveOrZero Long profileVersion,
        @NotBlank @Size(max = 100) @Pattern(regexp = "[^\\p{Cntrl}<>]*") String name,
        @NotNull DeviceType deviceType) { }
