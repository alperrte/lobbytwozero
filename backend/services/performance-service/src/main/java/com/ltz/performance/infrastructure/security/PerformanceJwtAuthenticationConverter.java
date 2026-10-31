package com.ltz.performance.infrastructure.security;

import com.ltz.performance.application.port.AuthenticatedActor;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

@Component
public class PerformanceJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {
    private static final Set<String> ROLES = Set.of("PLAYER", "CONTENT_CREATOR", "GAME_DEVELOPER", "MODERATOR", "ADMIN");

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        var authorities = new ArrayList<SimpleGrantedAuthority>();
        if (jwt.getClaim("roles") instanceof List<?> roles && roles.stream().allMatch(String.class::isInstance)) {
            roles.stream().map(String.class::cast).filter(ROLES::contains).distinct()
                    .forEach(role -> authorities.add(new SimpleGrantedAuthority("ROLE_" + role)));
        }
        return UsernamePasswordAuthenticationToken.authenticated(
                new AuthenticatedActor(UUID.fromString(jwt.getSubject())), null, authorities);
    }
}
