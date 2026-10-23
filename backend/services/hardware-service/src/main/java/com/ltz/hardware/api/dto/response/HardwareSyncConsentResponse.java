package com.ltz.hardware.api.dto.response;

import java.time.Instant;
import java.util.UUID;

public record HardwareSyncConsentResponse(long profileVersion, String status, UUID deviceId,
        UUID generation, Instant grantedAt, Instant revokedAt, long lastSequence,
        Instant lastCapturedAt, Instant lastSyncedAt) { }
