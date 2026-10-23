package com.ltz.hardware.api.dto.request;

import jakarta.validation.constraints.*;

public record ArchiveCatalogEntryRequest(
        @NotNull @PositiveOrZero Long version,
        @NotBlank @Size(max = 500) @Pattern(regexp = "[^\\p{Cntrl}<>]+") String reason) { }
