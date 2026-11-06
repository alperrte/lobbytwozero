package com.ltz.hardware.infrastructure.security;

import com.ltz.hardware.application.port.HardwareVisibilityPolicy;
import java.util.UUID;

/** Açıkça seçilen kapalı fallback; üretim bean'i UserServiceHardwareVisibilityPolicy'dir. */
public class UnavailableHardwareVisibilityPolicy implements HardwareVisibilityPolicy {
    @Override
    public Decision evaluate(UUID viewerId, UUID ownerId) {
        return new Decision(false, false);
    }
}
