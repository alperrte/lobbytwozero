package com.ltz.hardware.infrastructure.security;

import com.ltz.hardware.application.port.HardwareVisibilityPolicy;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** DEFERRED_CONTRACT: deny until real User/Social contracts are agreed and verified. */
@Component
public class UnavailableHardwareVisibilityPolicy implements HardwareVisibilityPolicy {
    @Override
    public Decision evaluate(UUID viewerId, UUID ownerId) {
        return new Decision(false, false);
    }
}
