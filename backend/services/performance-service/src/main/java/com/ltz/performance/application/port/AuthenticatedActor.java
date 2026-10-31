package com.ltz.performance.application.port;

import java.util.Objects;
import java.util.UUID;

/** Auth kullanıcı token'ından (at+jwt) doğrulanmış hesap kimliği. */
public record AuthenticatedActor(UUID userId) {
    public AuthenticatedActor { Objects.requireNonNull(userId); }
}
