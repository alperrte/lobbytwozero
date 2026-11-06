package com.ltz.hardware.infrastructure.security;

import com.ltz.hardware.application.port.HardwareReviewAudiencePolicy;
import java.util.UUID;
import java.util.Set;

/** Açıkça seçilen kapalı fallback; üretimde UserServiceReviewAudiencePolicy kullanılır. */
public class UnavailableHardwareReviewAudiencePolicy implements HardwareReviewAudiencePolicy {
    @Override
    public Decision evaluate(UUID viewerId, Set<UUID> authors) { return Decision.denyOthers(); }
}
