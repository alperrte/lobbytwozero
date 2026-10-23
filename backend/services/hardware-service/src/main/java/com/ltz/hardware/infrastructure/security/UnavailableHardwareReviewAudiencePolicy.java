package com.ltz.hardware.infrastructure.security;

import com.ltz.hardware.application.port.HardwareReviewAudiencePolicy;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class UnavailableHardwareReviewAudiencePolicy implements HardwareReviewAudiencePolicy {
    @Override
    public Decision evaluate(UUID viewerId) { return Decision.denyOthers(); }
}
