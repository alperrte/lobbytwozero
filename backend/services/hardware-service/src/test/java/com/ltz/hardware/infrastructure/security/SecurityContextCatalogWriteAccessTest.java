package com.ltz.hardware.infrastructure.security;

import static org.mockito.Mockito.mock;

import com.ltz.hardware.application.port.AuthenticatedActor;
import com.ltz.hardware.application.port.CatalogWriteAccess;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import static org.assertj.core.api.Assertions.*;

class SecurityContextCatalogWriteAccessTest {
    final SecurityContextCatalogWriteAccess access = new SecurityContextCatalogWriteAccess(new SecurityContextCurrentActor(mock(com.ltz.hardware.infrastructure.persistence.repository.HardwareAccountAccessGuard.class)));

    @AfterEach
    void clear() { SecurityContextHolder.clearContext(); }

    @Test
    void applicationBoundaryRequiresTrustedActorAndExplicitPermissionIndependentlyOfHttpFilter() {
        assertThatThrownBy(access::requireWriter).isInstanceOf(InsufficientAuthenticationException.class);
        var context = SecurityContextHolder.getContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken("spoofed", null,
                List.of(new SimpleGrantedAuthority(CatalogWriteAccess.PERMISSION))));
        assertThatThrownBy(access::requireWriter).isInstanceOf(InsufficientAuthenticationException.class);
        UUID id = UUID.randomUUID();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(new AuthenticatedActor(id), null, List.of()));
        assertThatThrownBy(access::requireWriter).isInstanceOf(AccessDeniedException.class);
        context.setAuthentication(new UsernamePasswordAuthenticationToken(new AuthenticatedActor(id), null,
                List.of(new SimpleGrantedAuthority(CatalogWriteAccess.PERMISSION))));
        assertThat(access.requireWriter()).isEqualTo(id);
    }
}
