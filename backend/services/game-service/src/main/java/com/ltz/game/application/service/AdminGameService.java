package com.ltz.game.application.service;

import com.ltz.game.api.dto.response.AdminGameResponse;
import com.ltz.game.application.mapper.GameViewMapper;
import com.ltz.game.application.port.Actor;
import com.ltz.game.domain.entity.Game;
import com.ltz.game.domain.entity.GameFieldOverride;
import com.ltz.game.domain.enums.ActorRole;
import com.ltz.game.domain.enums.AdminAuditAction;
import com.ltz.game.domain.enums.GameOrigin;
import com.ltz.game.domain.enums.GameStatus;
import com.ltz.game.domain.enums.MetadataCompleteness;
import com.ltz.game.domain.enums.OverridableField;
import com.ltz.game.domain.exception.ContentNotFoundException;
import com.ltz.game.domain.exception.GameConflictException;
import com.ltz.game.domain.exception.GameNotFoundException;
import com.ltz.game.domain.exception.InvalidGameRequestException;
import com.ltz.game.infrastructure.persistence.repository.GameFieldOverrideRepository;
import com.ltz.game.infrastructure.persistence.repository.GameRepository;
import java.time.Clock;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Yetkili oyun yönetimi (GAME-07). Oyunlar fiziksel olarak silinmez: Library/Review kayıtları başka servislerde
 * olduğundan referansları doğrulanamaz; bu yüzden yalnız arşivleme/geri alma vardır. Harici (EXTERNAL) oyunlarda
 * değiştirilen sağlayıcı alanları manuel kilitlenir ve senkronizasyon bu alanları ezmez.
 */
@Service @RequiredArgsConstructor
public class AdminGameService {
    private final GameRepository games;
    private final GameFieldOverrideRepository overrides;
    private final AdminAuditService audit;
    private final GameViewMapper views;
    private final Clock clock;
    private final MetadataProvenanceService provenance;
    private final GameEditApplier edits;

    @Transactional(readOnly = true)
    public AdminGameResponse get(Actor actor, UUID id) {
        actor.auditRole(ActorRole.ADMIN);
        return response(games.findById(id).orElseThrow(GameNotFoundException::new));
    }

    @Transactional
    public AdminGameResponse create(Actor actor, GameEdit edit) {
        ActorRole role = actor.auditRole(ActorRole.ADMIN);
        RequestRules.text(edit.title(), 256, "INVALID_TITLE");
        if (edit.gameType() == null) throw new InvalidGameRequestException("GAME_TYPE_REQUIRED");
        Game game = new Game();
        game.setOrigin(GameOrigin.INTERNAL);
        List<String> changed = new ArrayList<>();
        edits.apply(game, edit, changed, null);
        provenance.adminChanges(game,changed);
        refreshCompleteness(game);
        game.setUpdatedAt(clock.instant());
        game = games.saveAndFlush(game);
        audit.append(actor, role, AdminAuditAction.CREATE_GAME, game.getId(), evidence(edit), "fields=" + String.join(",", changed));
        return response(game);
    }

    @Transactional
    public AdminGameResponse update(Actor actor, UUID id, long version, GameEdit edit) {
        ActorRole role = actor.auditRole(ActorRole.ADMIN);
        Game game = lock(id);
        if (game.getVersion() != version) throw new GameConflictException("VERSION_CONFLICT");
        List<String> changed = new ArrayList<>();
        Set<OverridableField> manual = EnumSet.noneOf(OverridableField.class);
        edits.apply(game, edit, changed, game.getOrigin() == GameOrigin.EXTERNAL ? manual : null);
        if (changed.isEmpty()) return response(game);
        provenance.adminChanges(game,changed);
        refreshCompleteness(game);
        game.setUpdatedAt(clock.instant());
        games.saveAndFlush(game);
        markManual(game.getId(), manual, actor);
        audit.append(actor, role, AdminAuditAction.UPDATE_GAME, id, evidence(edit), "fields=" + String.join(",", changed));
        return response(game);
    }

    /** Görünürlüğü kapatır; kütüphane/inceleme referansları ve geçmiş korunur. Tekrar çağrı etkisizdir. */
    @Transactional
    public AdminGameResponse archive(Actor actor, UUID id, String reason) {
        ActorRole role = actor.auditRole(ActorRole.ADMIN);
        String text = RequestRules.reason(reason);
        Game game = lock(id);
        if (game.getStatus() == GameStatus.ARCHIVED) return response(game);
        game.setStatus(GameStatus.ARCHIVED);
        game.setEditorRank(null); game.setPopularityUpdatedAt(null); game.setEditorActorId(null); game.setEditorEvidence(null);
        game.setUpdatedAt(clock.instant());
        games.saveAndFlush(game);
        audit.append(actor, role, AdminAuditAction.ARCHIVE_GAME, id, text, null);
        return response(game);
    }

    @Transactional
    public AdminGameResponse restore(Actor actor, UUID id, String reason) {
        ActorRole role = actor.auditRole(ActorRole.ADMIN);
        String text = RequestRules.reason(reason);
        Game game = lock(id);
        if (game.getStatus() == GameStatus.ACTIVE) return response(game);
        game.setStatus(GameStatus.ACTIVE);
        game.setUpdatedAt(clock.instant());
        games.saveAndFlush(game);
        audit.append(actor, role, AdminAuditAction.RESTORE_GAME, id, text, null);
        return response(game);
    }

    /** Manuel kilidi kaldırır; sonraki senkronizasyon sağlayıcı değerini yeniden yazabilir. */
    @Transactional
    public AdminGameResponse releaseOverride(Actor actor, UUID id, OverridableField field, String reason) {
        ActorRole role = actor.auditRole(ActorRole.ADMIN);
        String text = RequestRules.reason(reason);
        Game game = lock(id);
        GameFieldOverride override = overrides.findByGameIdAndField(id, field).orElseThrow(() -> new ContentNotFoundException("OVERRIDE_NOT_FOUND"));
        overrides.delete(override);
        overrides.flush();
        audit.append(actor, role, AdminAuditAction.RELEASE_OVERRIDE, id, text, "field=" + field);
        return response(game);
    }

    private Game lock(UUID id) {
        Game game = games.lockGame(id).orElseThrow(GameNotFoundException::new);
        if (game.getStatus() == GameStatus.MERGED) throw new GameConflictException("GAME_MERGED");
        return game;
    }

    private String evidence(GameEdit edit) { return edit.localization()==null ? null : RequestRules.reason(edit.localization().evidence()); }

    /** INTERNAL oyunda doluluk yalnız mevcut verinin varlığını yansıtır; FULL iddiası üretilmez. */
    private static void refreshCompleteness(Game game) {
        if (game.getOrigin() != GameOrigin.INTERNAL || game.getMetadataCompleteness() == MetadataCompleteness.FULL) return;
        boolean hasContent = game.getDescription() != null || game.getReleaseDate() != null || !game.getTerms().isEmpty()
                || !game.getDevelopers().isEmpty() || !game.getPublishers().isEmpty() || !game.getLanguages().isEmpty() || !game.getMedia().isEmpty();
        game.setMetadataCompleteness(hasContent ? MetadataCompleteness.PARTIAL : MetadataCompleteness.INDEX_ONLY);
    }

    private void markManual(UUID gameId, Set<OverridableField> fields, Actor actor) {
        for (OverridableField field : fields) {
            if (overrides.findByGameIdAndField(gameId, field).isPresent()) continue;
            GameFieldOverride override = new GameFieldOverride();
            override.setGameId(gameId);
            override.setField(field);
            override.setActorId(actor.userId());
            override.setCreatedAt(clock.instant());
            overrides.save(override);
        }
    }

    private AdminGameResponse response(Game game) {
        // Yanıttaki sürüm, bu istekteki tüm flush'lardan sonraki nihai değerdir (istemcinin sonraki PATCH'i için).
        games.flush();
        return new AdminGameResponse(game.getId(), game.getStatus(), game.getVersion(), game.getUpdatedAt(), views.detail(game),
                overrides.findByGameIdOrderByFieldAsc(game.getId()).stream()
                        .map(override -> new AdminGameResponse.ManualOverride(override.getField(), override.getCreatedAt())).toList());
    }
}
