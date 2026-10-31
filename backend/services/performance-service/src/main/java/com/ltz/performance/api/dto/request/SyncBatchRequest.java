package com.ltz.performance.api.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * İki şema tek uçta yaşar (ikinci paralel senkronizasyon sistemi yoktur):
 * <ul>
 *   <li>v1 (Faz 1): yalnız zarf; {@code schemaVersion=1}, {@code clientSentAt}, {@code sampleCount}. Örnek içeriği yoktur.</li>
 *   <li>v2 (Faz 3): {@code schemaVersion=2}, {@code clientSentAt}, {@code sessionId} ve {@code samples}.
 *       {@code sampleCount} v2'de gönderilmez; sayı {@code samples} uzunluğundan türetilir.</li>
 * </ul>
 * Bilinmeyen alanlar Jackson ayarıyla reddedilir; şekil karışımı (v1'de örnek, v2'de sayı vb.) {@link #requireShape()} ile 400 verir.
 */
public record SyncBatchRequest(
        @NotNull @Min(SyncBatchRequest.SCHEMA_V1) @Max(SyncBatchRequest.SCHEMA_V2) Integer schemaVersion,
        @NotNull Instant clientSentAt,
        @Min(0) @Max(SyncBatchRequest.MAX_SAMPLES) Integer sampleCount,
        UUID sessionId,
        @Size(max = SyncBatchRequest.MAX_SAMPLES) List<@NotNull @Valid SampleItemRequest> samples) {
    public static final int SCHEMA_V1 = 1;
    public static final int SCHEMA_V2 = 2;
    public static final int MAX_SAMPLES = 500;

    public boolean isV2() { return schemaVersion != null && schemaVersion == SCHEMA_V2; }

    /** Şema ile alanların uyumunu denetler; uyumsuzluk {@link IllegalArgumentException} (→ 400 INVALID_REQUEST). */
    public void requireShape() {
        boolean valid = isV2()
                ? sessionId != null && samples != null && sampleCount == null
                : sampleCount != null && sessionId == null && samples == null;
        if (!valid) throw new IllegalArgumentException("Şema ile alanlar uyumsuz.");
    }

    /** Kabul edilen örnek sayısı: v1'de bildirilen, v2'de gönderilen örneklerin sayısı. */
    public int effectiveSampleCount() { return isV2() ? samples.size() : sampleCount; }

    /** v1 içerik özeti: Faz 1 ile birebir aynıdır; mevcut makbuzlar ve tekrar gönderimler bozulmaz. */
    public String canonicalV1() {
        return "v1|" + schemaVersion + "|" + clientSentAt.toEpochMilli() + "|" + sampleCount;
    }
}
