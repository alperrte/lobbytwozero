package com.ltz.user.api.dto.request;

import com.ltz.user.domain.enums.Theme;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * Tercihlerin tamamı (PUT). Her alan zorunludur; tanımsız alan ve tanımsız tema reddedilir. Dil değeri sunucuda
 * {@code tr}/{@code en} ile sınırlıdır.
 *
 * @param version istemcinin son gördüğü sürüm; hiç kaydedilmemişse 0
 */
public record UpdatePreferencesRequest(
        @NotNull @PositiveOrZero Long version,
        @NotNull @Size(max = 16) String locale,
        @NotNull Theme theme,
        @NotNull Boolean reducedMotion,
        @NotNull Boolean compactLayout) {
}
