package com.ltz.hardware.infrastructure.security;

import com.ltz.hardware.application.port.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import static org.assertj.core.api.Assertions.*;

class SecurityContextHardwareReviewModerationAccessTest {
    @AfterEach
    void clear() { SecurityContextHolder.clearContext(); }

    @Test
    void permissionCannotSubstituteForTrustedActorOrBeSubstitutedByCatalogPermission() {
        var id = UUID.randomUUID();
        var realActor = new SecurityContextCurrentActor();
        var access = new SecurityContextHardwareReviewModerationAccess(realActor);
        var authority = List.of(new SimpleGrantedAuthority(HardwareReviewModerationAccess.PERMISSION));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(new AuthenticatedActor(id), null, authority));
        assertThat(access.requireModeratorId()).isEqualTo(id);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(new AuthenticatedActor(id), null,
                List.of(new SimpleGrantedAuthority(CatalogWriteAccess.PERMISSION))));
        assertThatThrownBy(access::requireModeratorId).isInstanceOf(AccessDeniedException.class);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("untrusted", null, authority));
        assertThatThrownBy(access::requireModeratorId).isInstanceOf(AuthenticationException.class);
        SecurityContextHolder.clearContext();
        var mockedActorAccess = new SecurityContextHardwareReviewModerationAccess(() -> id);
        assertThatThrownBy(mockedActorAccess::requireModeratorId).isInstanceOf(AccessDeniedException.class);
    }
}
