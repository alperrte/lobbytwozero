package com.ltz.performance.api.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import tools.jackson.databind.annotation.JsonDeserialize;

/**
 * Tek ölçüm okuması. Birim istekte yoktur: her metriğin kanonik birimi sabittir. Ölçülemeyen değer için örnek
 * gönderilmez; {@code value} null/eksik olamaz ve 0 ile doldurulmaz.
 */
public record SampleItemRequest(
        @NotNull Instant capturedAt,
        @NotNull @Size(max = 24) String metric,
        @NotNull @JsonDeserialize(using = StrictNumberDeserializer.class) Double value) { }
