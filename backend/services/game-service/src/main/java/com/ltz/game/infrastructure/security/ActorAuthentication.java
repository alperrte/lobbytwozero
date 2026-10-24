package com.ltz.game.infrastructure.security;

import com.ltz.game.application.port.Actor;
import java.util.Objects;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

/** Doğrulanmış JWT'den türetilmiş kimlik; yetkiler yalnız çözümlenmiş rollerdir. */
final class ActorAuthentication extends AbstractAuthenticationToken {
    private final transient Actor actor;
    private final transient Jwt jwt;

    ActorAuthentication(Actor actor, Jwt jwt) {
        super(actor.roles().stream().map(role -> new SimpleGrantedAuthority("ROLE_" + role.name())).toList());
        this.actor = actor;
        this.jwt = jwt;
        setAuthenticated(true);
    }

    @Override public Object getPrincipal() { return actor; }
    @Override public Object getCredentials() { return jwt; }
    @Override public String getName() { return actor.userId().toString(); }

    @Override
    public boolean equals(Object other) {
        return other instanceof ActorAuthentication that && super.equals(that) && actor.equals(that.actor) && Objects.equals(jwt, that.jwt);
    }

    @Override
    public int hashCode() { return Objects.hash(super.hashCode(), actor, jwt); }
}
