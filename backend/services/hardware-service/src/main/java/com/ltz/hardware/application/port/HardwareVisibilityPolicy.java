package com.ltz.hardware.application.port;

import java.util.UUID;

/**
 * Trusted User/Social decision; no request headers or client-supplied relationship claims.
 * allowed requires verified global visibility and absence of blocks in either direction.
 * friends requires verified friendship; unknown, blocked or unavailable decisions deny.
 */
public interface HardwareVisibilityPolicy {
    record Decision(boolean allowed, boolean friends) { }
    Decision evaluate(UUID viewerId, UUID ownerId);
}
