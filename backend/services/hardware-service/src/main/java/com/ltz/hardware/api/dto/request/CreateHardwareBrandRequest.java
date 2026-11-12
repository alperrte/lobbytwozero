package com.ltz.hardware.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateHardwareBrandRequest(
        @NotBlank @Size(max = 100) @Pattern(regexp = "[^\\p{Cntrl}<>]+") String name,
        @NotBlank @Size(max = 500) @Pattern(regexp = "[^\\p{Cntrl}<>]+") String reason) { }
