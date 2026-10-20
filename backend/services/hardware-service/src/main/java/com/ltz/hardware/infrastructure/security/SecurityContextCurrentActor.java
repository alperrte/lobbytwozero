package com.ltz.hardware.infrastructure.security;

import com.ltz.hardware.application.port.AuthenticatedActor;
import com.ltz.hardware.application.port.CurrentActor;
import java.util.UUID;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class SecurityContextCurrentActor implements CurrentActor {
    @Override
    public UUID requireUserId() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof AuthenticatedActor(UUID userId))) {
            throw new InsufficientAuthenticationException("Doğrulanmış kullanıcı gerekli");
        }
        return userId;
    }
}
