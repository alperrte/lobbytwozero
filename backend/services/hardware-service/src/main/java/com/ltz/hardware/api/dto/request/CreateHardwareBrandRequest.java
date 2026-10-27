package com.ltz.hardware.api.dto.request;

import jakarta.validation.constraints.*;

public record CreateHardwareBrandRequest(
        @NotBlank @Size(max = 100) @Pattern(regexp = "[^\\p{Cntrl}<>]+") String name,
        @NotBlank @Size(max = 500) @Pattern(regexp = "[^\\p{Cntrl}<>]+") String reason) { }
