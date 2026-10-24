package com.ltz.game.application.service;

import com.ltz.game.application.port.Actor;
import com.ltz.game.domain.entity.GameAdminAudit;
import com.ltz.game.domain.enums.ActorRole;
import com.ltz.game.domain.enums.AdminAuditAction;
import com.ltz.game.infrastructure.persistence.repository.GameAdminAuditRepository;
import java.time.Clock;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Yönetim işlemleri iş verisiyle aynı transaction'da append-only kayda yazılır. İçerik metni kaydedilmez. */
@Service @RequiredArgsConstructor
public class AdminAuditService {
    private final GameAdminAuditRepository audits;
    private final Clock clock;

    @Transactional(propagation = Propagation.MANDATORY)
    public void append(Actor actor, ActorRole role, AdminAuditAction action, UUID gameId, String reason, String details) {
        var audit = new GameAdminAudit();
        audit.setGameId(gameId);
        audit.setActorId(actor.userId());
        audit.setActorRole(role);
        audit.setAction(action);
        audit.setReason(reason);
        audit.setDetails(details);
        audit.setCreatedAt(clock.instant());
        audits.saveAndFlush(audit);
    }
}
