package com.ltz.game.application.service;

import com.ltz.game.api.dto.response.ModerationResponse;
import com.ltz.game.application.port.Actor;
import com.ltz.game.domain.entity.DeveloperContentBase;
import com.ltz.game.domain.enums.ActorRole;
import com.ltz.game.domain.enums.AdminAuditAction;
import com.ltz.game.domain.enums.ContentStatus;
import com.ltz.game.domain.enums.ModerationAction;
import com.ltz.game.domain.enums.ModerationTarget;
import com.ltz.game.domain.exception.ContentNotFoundException;
import com.ltz.game.domain.exception.GameNotFoundException;
import com.ltz.game.domain.exception.InvalidGameRequestException;
import com.ltz.game.infrastructure.persistence.repository.GameDeveloperContentRepository;
import com.ltz.game.infrastructure.persistence.repository.GameDeveloperMediaRepository;
import com.ltz.game.infrastructure.persistence.repository.GameRepository;
import com.ltz.game.infrastructure.persistence.repository.GameUpdateNoteRepository;
import java.time.Clock;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Moderatör/admin gizleme ve geri alma. Gerekçe zorunludur; eylem denetim kaydına yazılır. */
@Service @RequiredArgsConstructor
public class DeveloperContentModerationService {
    private static final String NOT_FOUND = "CONTENT_NOT_FOUND";
    private final GameRepository games;
    private final GameDeveloperContentRepository contents;
    private final GameDeveloperMediaRepository media;
    private final GameUpdateNoteRepository notes;
    private final AdminAuditService audit;
    private final Clock clock;

    @Transactional
    public ModerationResponse apply(Actor actor, UUID gameId, ModerationTarget target, UUID targetId, ModerationAction action, String reason) {
        ActorRole role = actor.auditRole(ActorRole.ADMIN, ActorRole.MODERATOR);
        String text = RequestRules.reason(reason);
        games.lockGame(gameId).orElseThrow(GameNotFoundException::new);
        if ((target == ModerationTarget.DESCRIPTION) != (targetId == null)) throw new InvalidGameRequestException("INVALID_TARGET");
        DeveloperContentBase item = switch (target) {
            case DESCRIPTION -> contents.findById(gameId).orElseThrow(() -> new ContentNotFoundException(NOT_FOUND));
            case MEDIA -> media.findByIdAndGameId(targetId, gameId).filter(found -> found.getStatus() != ContentStatus.DELETED)
                    .orElseThrow(() -> new ContentNotFoundException(NOT_FOUND));
            case UPDATE_NOTE -> notes.findByIdAndGameId(targetId, gameId).filter(found -> found.getStatus() != ContentStatus.DELETED)
                    .orElseThrow(() -> new ContentNotFoundException(NOT_FOUND));
        };
        ContentStatus wanted = action == ModerationAction.HIDE ? ContentStatus.HIDDEN : ContentStatus.VISIBLE;
        if (item.getStatus() != wanted) {
            item.setStatus(wanted);
            item.setModeratedBy(actor.userId());
            item.setModeratedAt(clock.instant());
            item.setModerationReason(action == ModerationAction.HIDE ? text : null);
            audit.append(actor, role, action == ModerationAction.HIDE ? AdminAuditAction.HIDE_DEVELOPER_CONTENT : AdminAuditAction.RESTORE_DEVELOPER_CONTENT,
                    gameId, text, "target=" + target + (targetId == null ? "" : ",targetId=" + targetId));
            flush(target);
        }
        return new ModerationResponse(target, targetId, item.getStatus());
    }

    private void flush(ModerationTarget target) {
        switch (target) {
            case DESCRIPTION -> contents.flush();
            case MEDIA -> media.flush();
            case UPDATE_NOTE -> notes.flush();
        }
    }
}
