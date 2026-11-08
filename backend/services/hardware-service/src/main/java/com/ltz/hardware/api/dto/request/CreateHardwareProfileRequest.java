package com.ltz.hardware.api.dto.request;

import com.ltz.hardware.domain.enums.CaseColor;
import com.ltz.hardware.domain.enums.DeviceType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** {@code caseColor} is optional; when omitted the profile starts with {@link CaseColor#BLACK}. */
public record CreateHardwareProfileRequest(
        @NotBlank @Size(max = 100) @Pattern(regexp = "[^\\p{Cntrl}<>]*") String name,
        @NotNull DeviceType deviceType,
        CaseColor caseColor) { }
