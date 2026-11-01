package com.ltz.messaging.api.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/** Okunan son mesajın sırası ({@code seq}); işaretçi geri gitmez. */
public record MarkReadRequest(@NotNull @PositiveOrZero Long seq) {
}
