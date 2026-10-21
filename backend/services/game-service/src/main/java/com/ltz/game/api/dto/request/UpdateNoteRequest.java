package com.ltz.game.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** {@code version}: güncellemede zorunlu (iyimser kilit); oluşturmada gönderilmez. */
public record UpdateNoteRequest(@NotBlank @Size(max=120) String title, @NotBlank @Size(max=4000) String body, Long version) { }
