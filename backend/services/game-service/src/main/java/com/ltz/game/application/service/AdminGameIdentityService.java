package com.ltz.game.application.service;

import com.ltz.game.application.port.Actor;
import com.ltz.game.domain.enums.ActorRole;
import com.ltz.game.domain.exception.AccessForbiddenException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** ADMIN sınırı. Dış referansları doğruladığı iddia edilmez; güvenli girişler fail closed çalışır. */
@Service @RequiredArgsConstructor
public class AdminGameIdentityService {
    private final GameIdentityService identity;

    public UUID reassign(Actor actor, String provider, String externalId, UUID expected, UUID target, String evidence) {
        requireAdmin(actor);
        return identity.safeReassign(provider, externalId, expected, target,
                new GameIdentityService.Verification(actor.userId(), evidence));
    }

    public UUID split(Actor actor, String provider, String externalId, UUID expected, String evidence) {
        requireAdmin(actor);
        return identity.safeSplit(provider, externalId, expected,
                new GameIdentityService.Verification(actor.userId(), evidence));
    }

    private void requireAdmin(Actor actor) {
        if (actor == null || !actor.has(ActorRole.ADMIN)) throw new AccessForbiddenException();
    }
}
