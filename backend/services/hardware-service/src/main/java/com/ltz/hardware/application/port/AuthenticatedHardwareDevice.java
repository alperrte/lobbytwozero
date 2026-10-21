package com.ltz.hardware.application.port;

import java.util.Objects;
import java.util.UUID;

/** Only a future verified device authenticator may create a production principal. */
public record AuthenticatedHardwareDevice(UUID userId, UUID deviceId) {
    public AuthenticatedHardwareDevice { Objects.requireNonNull(userId); Objects.requireNonNull(deviceId); }
}
