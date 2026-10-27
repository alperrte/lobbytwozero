package com.ltz.hardware.infrastructure.security;

import com.ltz.hardware.application.port.CatalogWriteAccess;
import com.ltz.hardware.application.port.CurrentActor;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class SecurityContextCatalogWriteAccess implements CatalogWriteAccess {
    private final CurrentActor actor;

    public SecurityContextCatalogWriteAccess(CurrentActor actor) { this.actor = actor; }

    @Override
    public UUID requireWriter() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            throw new org.springframework.security.authentication.InsufficientAuthenticationException("Doğrulanmış kimlik gerekli");
        }
        UUID userId = actor.requireUserId();
        boolean allowed = authentication.getAuthorities().stream().anyMatch(a -> PERMISSION.equals(a.getAuthority()));
        if (!allowed) throw new AccessDeniedException("Katalog yazma izni gerekli");
        return userId;
    }
}
