package com.ltz.user.api.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * Bağlı hesabın açık profilde gösterilme izni. Tanımsız alan reddedilir.
 *
 * @param version istemcinin {@code GET /me/linked-accounts} yanıtında gördüğü sürüm
 */
public record UpdateLinkedAccountRequest(
        @NotNull @PositiveOrZero Long version,
        @NotNull Boolean showOnProfile) {
}
