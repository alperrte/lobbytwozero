package com.ltz.performance.application.port;

import java.util.Objects;
import java.util.UUID;

/** Auth'un performance-service hedefli cihaz token'ından (dev+jwt) doğrulanmış cihaz ve sahip hesap. */
public record AuthenticatedPerformanceDevice(UUID userId, UUID deviceId) {
    public AuthenticatedPerformanceDevice { Objects.requireNonNull(userId); Objects.requireNonNull(deviceId); }
}
