package com.ltz.hardware.api.dto.request;

import com.ltz.hardware.domain.enums.ComponentCategory;
import com.ltz.hardware.domain.enums.HardwarePrivacyField;
import com.ltz.hardware.domain.enums.HardwareVisibility;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.Map;

public record UpdateHardwarePrivacyRequest(
        @NotNull @PositiveOrZero Long profileVersion,
        @NotNull HardwareVisibility visibility,
        @NotNull @Size(max = 6) Map<@NotNull HardwarePrivacyField, @NotNull HardwareVisibility> fields,
        @NotNull @Size(max = 12) Map<@NotNull ComponentCategory, @NotNull HardwareVisibility> categories) { }
