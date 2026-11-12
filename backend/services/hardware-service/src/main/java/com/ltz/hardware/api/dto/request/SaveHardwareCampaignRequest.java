package com.ltz.hardware.api.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Create uses productId and no version; update uses version and an immutable product reference. */
public record SaveHardwareCampaignRequest(
        UUID productId,
        @PositiveOrZero Long version,
        @NotBlank @Size(max = 160) @Pattern(regexp = "[^\\p{Cntrl}<>]+") String title,
        @NotBlank @Size(max = 100) @Pattern(regexp = "[^\\p{Cntrl}<>]+") String sourceName,
        @NotBlank @Size(max = 2048) String sourceUrl,
        @NotNull @DecimalMin(value = "0.00", inclusive = false) @Digits(integer = 10, fraction = 2) BigDecimal originalPrice,
        @NotNull @DecimalMin(value = "0.00", inclusive = false) @Digits(integer = 10, fraction = 2) BigDecimal salePrice,
        @NotNull @Pattern(regexp = "[A-Z]{3}") String currency,
        @NotNull Instant startsAt,
        @NotNull Instant endsAt,
        @NotNull Instant observedAt,
        @NotBlank @Size(max = 500) @Pattern(regexp = "[^\\p{Cntrl}<>]+") String reason) { }
