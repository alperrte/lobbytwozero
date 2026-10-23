package com.ltz.hardware.application.port;

import java.security.Principal;
import java.util.Objects;
import java.util.UUID;

/** Only a trusted authentication adapter may supply this principal. */
public record AuthenticatedActor(UUID userId) implements Principal {
    public AuthenticatedActor {
        Objects.requireNonNull(userId);
    }

    @Override
    public String getName() {
        return userId.toString();
    }
}
