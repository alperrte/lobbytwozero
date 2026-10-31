package com.ltz.performance.api.dto.response;

import java.time.Instant;
import java.util.UUID;

/** {@code replayed=true}: aynı Idempotency-Key ve aynı içerikle önceden kabul edilmiş toplu gönderim. */
public record SyncBatchResponse(UUID batchId, int sampleCount, Instant receivedAt, boolean replayed) { }
