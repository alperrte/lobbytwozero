package com.ltz.hardware.infrastructure.security;

import com.ltz.hardware.application.port.CurrentActor;
import com.ltz.hardware.application.port.HardwareReviewModerationAccess;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class SecurityContextHardwareReviewModerationAccess implements HardwareReviewModerationAccess {
    private final CurrentActor actor;
    public SecurityContextHardwareReviewModerationAccess(CurrentActor actor) { this.actor = actor; }

    @Override
    public UUID requireModeratorId() {
        var id = actor.requireUserId();
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getAuthorities().stream().noneMatch(a -> PERMISSION.equals(a.getAuthority()))) {
            throw new AccessDeniedException("Moderasyon yetkisi gerekli");
        }
        return id;
    }
}
