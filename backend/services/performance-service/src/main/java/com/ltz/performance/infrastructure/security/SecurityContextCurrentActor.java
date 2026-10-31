package com.ltz.performance.infrastructure.security;

import com.ltz.performance.application.port.AuthenticatedActor;
import com.ltz.performance.application.port.CurrentActor;
import com.ltz.performance.infrastructure.persistence.repository.PerformanceAccountAccessGuard;
import java.util.UUID;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class SecurityContextCurrentActor implements CurrentActor {
    private final PerformanceAccountAccessGuard guard;

    public SecurityContextCurrentActor(PerformanceAccountAccessGuard guard) { this.guard = guard; }

    @Override
    public UUID requireUserId() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof AuthenticatedActor(UUID userId))) {
            throw new InsufficientAuthenticationException("Doğrulanmış kullanıcı gerekli");
        }
        guard.requireActive(userId);
        return userId;
    }
}
