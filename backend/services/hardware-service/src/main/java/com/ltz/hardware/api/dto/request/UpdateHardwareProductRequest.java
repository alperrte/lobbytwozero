package com.ltz.hardware.api.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.Set;
import java.util.UUID;

public record UpdateHardwareProductRequest(
        @NotNull @PositiveOrZero Long version,
        @NotBlank @Size(max = 160) @Pattern(regexp = "[^\\p{Cntrl}<>]+") String model,
        @Size(max = 100) Set<@NotNull UUID> brandIds,
        @Valid SpecificationsRequest specifications,
        @NotBlank @Size(max = 500) @Pattern(regexp = "[^\\p{Cntrl}<>]+") String reason) { }
