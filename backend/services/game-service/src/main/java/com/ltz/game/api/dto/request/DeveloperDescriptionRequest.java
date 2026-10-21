package com.ltz.game.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** {@code version}: açıklama zaten varsa mevcut sürüm (iyimser kilit); ilk oluşturmada gönderilmez. */
public record DeveloperDescriptionRequest(@NotBlank @Size(max=8000) String description, Long version) { }
