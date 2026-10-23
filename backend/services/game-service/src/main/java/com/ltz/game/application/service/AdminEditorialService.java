package com.ltz.game.application.service;

import com.ltz.game.application.port.Actor;
import com.ltz.game.domain.enums.ActorRole;
import com.ltz.game.domain.enums.AdminAuditAction;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Editör (popüler) listesinin yetkili HTTP girişi; doğrulanmış aktör ve gerekçe EditorialCatalogService'e iletilir. */
@Service @RequiredArgsConstructor
public class AdminEditorialService {
    private final EditorialCatalogService editorial;
    private final AdminAuditService audit;

    @Transactional
    public void replace(Actor actor, List<UUID> orderedIds, String reason) {
        ActorRole role = actor.auditRole(ActorRole.ADMIN);
        String text = RequestRules.reason(reason);
        editorial.replace(orderedIds, actor.userId(), text);
        audit.append(actor, role, AdminAuditAction.SET_EDITORIAL, null, text, "count=" + orderedIds.size());
    }
}
