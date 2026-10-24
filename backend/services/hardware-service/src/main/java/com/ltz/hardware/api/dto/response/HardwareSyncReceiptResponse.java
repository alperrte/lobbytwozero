package com.ltz.hardware.api.dto.response;

import java.time.Instant;
import java.util.UUID;

public record HardwareSyncReceiptResponse(UUID idempotencyKey, long profileVersion, long sequence,
        Instant capturedAt, Instant appliedAt, int receivedComponents, int created, int updated, int removed) { }
