package com.ltz.game.application.port;

import com.ltz.game.domain.enums.ActorRole;
import java.util.Collection;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

/**
 * Doğrulanmış kimlik. Yalnız merkezi güvenlik katmanı üretir; controller ve servisler JWT claim'i okumaz.
 * Auth Service entegrasyonunda bu sözleşme korunur, yalnız üreten güvenlik yapılandırması değişir.
 */
public record Actor(UUID userId, Set<ActorRole> roles) {
    public Actor {
        if (userId == null) throw new IllegalArgumentException("Aktör kimliği zorunludur.");
        roles = roles == null || roles.isEmpty() ? Set.of() : Set.copyOf(EnumSet.copyOf(roles));
    }
    public static Actor of(UUID userId, Collection<ActorRole> roles) {
        return new Actor(userId, roles == null || roles.isEmpty() ? Set.of() : EnumSet.copyOf(roles));
    }
    public boolean has(ActorRole role) { return roles.contains(role); }
    /** Denetim kaydında, işlemi yetkilendiren en yüksek rol. */
    public ActorRole auditRole(ActorRole... acceptable) {
        for (ActorRole role : acceptable) if (roles.contains(role)) return role;
        throw new com.ltz.game.domain.exception.AccessForbiddenException();
    }
}
