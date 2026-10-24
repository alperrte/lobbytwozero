package com.ltz.hardware.infrastructure.security;

import com.ltz.hardware.application.port.AuthenticatedActor;
import com.ltz.hardware.application.port.CatalogWriteAccess;
import com.ltz.hardware.application.port.HardwareReviewModerationAccess;
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
public class HardwareJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {
    private static final String ADMIN = "ADMIN";
    private static final Set<String> ROLES = Set.of("PLAYER", "CONTENT_CREATOR", "GAME_DEVELOPER", "MODERATOR", ADMIN);

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        var authorities = new ArrayList<SimpleGrantedAuthority>();
        if (jwt.getClaim("roles") instanceof List<?> roles && roles.stream().allMatch(String.class::isInstance)) {
            roles.stream().map(String.class::cast).filter(ROLES::contains).distinct()
                    .forEach(role -> authorities.add(new SimpleGrantedAuthority("ROLE_" + role)));
            if (roles.contains(ADMIN)) authorities.add(new SimpleGrantedAuthority(CatalogWriteAccess.PERMISSION));
            if (roles.contains(ADMIN) || roles.contains("MODERATOR")) {
                authorities.add(new SimpleGrantedAuthority(HardwareReviewModerationAccess.PERMISSION));
            }
        }
        return UsernamePasswordAuthenticationToken.authenticated(
                new AuthenticatedActor(UUID.fromString(jwt.getSubject())), null, authorities);
    }
}
