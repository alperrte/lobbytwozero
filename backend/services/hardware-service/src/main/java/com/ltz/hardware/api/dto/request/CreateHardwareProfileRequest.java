package com.ltz.hardware.api.dto.request;

import com.ltz.hardware.domain.enums.DeviceType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateHardwareProfileRequest(
        @NotBlank @Size(max = 100) @Pattern(regexp = "[^\\p{Cntrl}<>]*") String name,
        @NotNull DeviceType deviceType) { }
