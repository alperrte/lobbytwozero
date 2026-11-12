package com.ltz.messaging.api.dto.request;

import com.ltz.messaging.domain.enums.GifProvider;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Gönderilecek platform GIF'i (MSG-22): yalnız sağlayıcı ve GIF kimliği; adres istemciden alınmaz. */
public record GifReferenceRequest(
        @NotNull GifProvider provider,
        @NotBlank @Size(max = 64) @Pattern(regexp = "[A-Za-z0-9]+") String id) {
}
