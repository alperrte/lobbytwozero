package com.ltz.performance.api.dto.response;

import java.time.Instant;
import java.util.UUID;

/**
 * {@code replayed=true}: aynı Idempotency-Key ve aynı içerikle önceden kabul edilmiş toplu gönderim; sayılar ilk
 * kabulün sonucudur. v1'de {@code storedSamples}/{@code duplicateSamples} her zaman 0'dır (örnek içeriği yok).
 * {@code duplicateSamples}: oturumda zaten bulunan (aynı metrik ve zaman) için yeniden yazılmayan örnekler.
 */
public record SyncBatchResponse(UUID batchId, int sampleCount, int storedSamples, int duplicateSamples,
        Instant receivedAt, boolean replayed) { }
