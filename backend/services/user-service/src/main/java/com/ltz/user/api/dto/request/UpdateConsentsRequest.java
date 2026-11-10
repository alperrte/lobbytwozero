package com.ltz.user.api.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * Rızaların tamamı (PUT). Her alan zorunludur; eksik alan sessizce "rıza yok" sayılmaz, reddedilir.
 *
 * @param version istemcinin son gördüğü sürüm; hiç kaydedilmemişse 0
 * @param recommendation kişiselleştirilmiş öneri için favori/bitirilen oyun verisinin işlenmesine rıza
 */
public record UpdateConsentsRequest(
        @NotNull @PositiveOrZero Long version,
        @NotNull Boolean recommendation) {
}
