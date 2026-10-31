package com.ltz.performance.api.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

/**
 * Faz 1 yalnız toplu gönderim zarfını kabul eder; ölçüm içeriği ve işlenmesi sonraki fazlardadır.
 * Bilinmeyen alanlar Jackson ayarıyla reddedilir.
 */
public record SyncBatchRequest(
        @NotNull @Min(SyncBatchRequest.SCHEMA_VERSION) @Max(SyncBatchRequest.SCHEMA_VERSION) Integer schemaVersion,
        @NotNull Instant clientSentAt,
        @NotNull @Min(0) @Max(SyncBatchRequest.MAX_SAMPLES) Integer sampleCount) {
    public static final int SCHEMA_VERSION = 1;
    public static final int MAX_SAMPLES = 500;

    /** İçerik özeti: aynı anahtarla farklı içerik gönderimini yakalamak için kanonik biçim. */
    public String canonical() {
        return "v1|" + schemaVersion + "|" + clientSentAt.toEpochMilli() + "|" + sampleCount;
    }
}
