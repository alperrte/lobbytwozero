package com.ltz.user.api.dto.request;

import com.ltz.user.domain.enums.GuestAccess;
import com.ltz.user.domain.enums.Visibility;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * Gizlilik ayarlarının tamamı (PUT). Her alan zorunludur; tanımsız alan ve tanımsız değer reddedilir.
 *
 * @param version istemcinin son gördüğü sürüm; hiç kaydedilmemişse 0
 */
public record UpdatePrivacyRequest(
        @NotNull @PositiveOrZero Long version,
        @NotNull Visibility friendsList,
        @NotNull Visibility library,
        @NotNull Visibility hardware,
        @NotNull Visibility performance,
        @NotNull GuestAccess guestProfileAccess) {
}
