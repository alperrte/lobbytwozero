package com.ltz.game.application.service;

import com.ltz.game.api.dto.response.AdminGameResponse;
import com.ltz.game.application.mapper.GameViewMapper;
import com.ltz.game.application.port.Actor;
import com.ltz.game.domain.entity.CatalogTerm;
import com.ltz.game.domain.entity.Game;
import com.ltz.game.domain.entity.GameFieldOverride;
import com.ltz.game.domain.entity.GameMedia;
import com.ltz.game.domain.enums.*;
import com.ltz.game.domain.exception.*;
import com.ltz.game.infrastructure.persistence.repository.CatalogTermRepository;
import com.ltz.game.infrastructure.persistence.repository.GameFieldOverrideRepository;
import com.ltz.game.infrastructure.persistence.repository.GameRepository;
import java.time.Clock;
import java.util.*;
import java.util.stream.Collectors;
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
    private static final int MAX_NAMES = 100;
    private final GameRepository games;
    private final CatalogTermRepository terms;
    private final GameFieldOverrideRepository overrides;
    private final AdminAuditService audit;
    private final GameViewMapper views;
    private final MediaUrlPolicy urls;
    private final Clock clock;
    private final LocalizationService localization;
    private final MetadataProvenanceService provenance;

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
        apply(game, edit, changed, null);
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
        apply(game, edit, changed, game.getOrigin() == GameOrigin.EXTERNAL ? manual : null);
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

    private void apply(Game game, GameEdit edit, List<String> changed, Set<OverridableField> manual) {
        applyTexts(game, edit, changed, manual);
        applyScalars(game, edit, changed, manual);
        applyCollections(game, edit, changed, manual);
        if (edit.coverUrl() != null) applyCover(game, edit.coverUrl(), changed);
        if (localization.apply(game,edit.localization())) mark(changed,"localization",manual,OverridableField.LOCALIZATION);
    }

    private String evidence(GameEdit edit) { return edit.localization()==null ? null : RequestRules.reason(edit.localization().evidence()); }

    private void applyTexts(Game game, GameEdit edit, List<String> changed, Set<OverridableField> manual) {
        if (edit.title() != null) {
            String title = RequestRules.text(edit.title(), 256, "INVALID_TITLE");
            if (!title.equals(game.getTitle())) {
                game.setTitle(title);
                game.setNormalizedTitle(CatalogImportService.normalize(title));
                mark(changed, "title", manual, OverridableField.TITLE);
            }
        }
        if (edit.description() != null) {
            String description = RequestRules.optionalText(edit.description(), 16000, "INVALID_DESCRIPTION");
            if (!Objects.equals(description, game.getDescription())) {
                game.setDescription(description);
                mark(changed, "description", manual, OverridableField.DESCRIPTION);
            }
        }
    }

    private void applyScalars(Game game, GameEdit edit, List<String> changed, Set<OverridableField> manual) {
        if (edit.releaseDate() != null && !edit.releaseDate().equals(game.getReleaseDate())) {
            if (edit.releaseDate().getYear() < 1 || edit.releaseDate().getYear() > 9999) throw new InvalidGameRequestException("INVALID_RELEASE_DATE");
            game.setReleaseDate(edit.releaseDate());
            mark(changed, "releaseDate", manual, OverridableField.RELEASE_DATE);
        }
        if (edit.earlyAccess() != null && !edit.earlyAccess().equals(game.getEarlyAccess())) {
            game.setEarlyAccess(edit.earlyAccess());
            changed.add("earlyAccess");
        }
        if (edit.gameType() != null && edit.gameType() != game.getGameType()) {
            game.setGameType(edit.gameType());
            mark(changed, "gameType", manual, OverridableField.GAME_TYPE);
        }
    }

    private void applyCollections(Game game, GameEdit edit, List<String> changed, Set<OverridableField> manual) {
        if (edit.languages() != null && replace(game.getLanguages(), languages(edit.languages()))) changed.add("languages");
        if (edit.developers() != null && replace(game.getDevelopers(), names(edit.developers()))) mark(changed, "developers", manual, OverridableField.DEVELOPERS);
        if (edit.publishers() != null && replace(game.getPublishers(), names(edit.publishers()))) mark(changed, "publishers", manual, OverridableField.PUBLISHERS);
        applyTerms(game, TermKind.GENRE, edit.genres(), "genres", changed);
        applyTerms(game, TermKind.CATEGORY, edit.categories(), "categories", changed);
        applyTerms(game, TermKind.TAG, edit.tags(), "tags", changed);
        applyTerms(game, TermKind.PLATFORM, edit.platforms(), "platforms", changed);
    }

    private static void mark(List<String> changed, String name, Set<OverridableField> manual, OverridableField field) {
        changed.add(name);
        if (manual != null) manual.add(field);
    }

    private static boolean replace(Set<String> target, Set<String> wanted) {
        if (target.equals(wanted)) return false;
        target.clear();
        target.addAll(wanted);
        return true;
    }

    private static Set<String> languages(Set<String> values) {
        if (values.size() > MAX_NAMES) throw new InvalidGameRequestException("INVALID_LANGUAGE");
        for (String value : values) {
            if (value == null || !RequestRules.LANGUAGE.matcher(value).matches()) throw new InvalidGameRequestException("INVALID_LANGUAGE");
        }
        return new HashSet<>(values);
    }

    private static Set<String> names(Set<String> values) {
        if (values.size() > MAX_NAMES) throw new InvalidGameRequestException("INVALID_NAME");
        return values.stream().map(value -> RequestRules.text(value, 256, "INVALID_NAME")).collect(Collectors.toCollection(HashSet::new));
    }

    private void applyTerms(Game game, TermKind kind, Set<String> codes, String name, List<String> changed) {
        if (codes == null) return;
        if (codes.size() > MAX_NAMES) throw new InvalidGameRequestException("INVALID_TERM");
        for (String code : codes) {
            if (code == null || !RequestRules.TERM_CODE.matcher(code).matches()) throw new InvalidGameRequestException("INVALID_TERM");
        }
        Set<String> current = game.getTerms().stream().filter(term -> term.getKind() == kind).map(CatalogTerm::getCode).collect(Collectors.toSet());
        if (current.equals(codes)) return;
        game.getTerms().removeIf(term -> term.getKind() == kind);
        for (String code : codes) game.getTerms().add(terms.findByKindAndCode(kind, code).orElseGet(() -> newTerm(kind, code)));
        changed.add(name);
    }

    private CatalogTerm newTerm(TermKind kind, String code) {
        CatalogTerm term = new CatalogTerm();
        term.setKind(kind);
        term.setCode(code);
        return terms.saveAndFlush(term);
    }

    private void applyCover(Game game, String value, List<String> changed) {
        List<GameMedia> covers = game.getMedia().stream().filter(media -> media.getType() == MediaType.COVER)
                .sorted(Comparator.comparingInt(GameMedia::getSortOrder).thenComparing(GameMedia::getId)).toList();
        if (value.isBlank()) {
            if (covers.isEmpty()) return;
            game.getMedia().removeAll(covers);
            changed.add("coverUrl");
            return;
        }
        String url = urls.validate(DeveloperMediaType.IMAGE, value);
        if (covers.isEmpty()) {
            GameMedia cover = new GameMedia();
            cover.setGame(game);
            cover.setType(MediaType.COVER);
            cover.setUrl(url);
            cover.setProvider("ADMIN");
            cover.setSortOrder(0);
            game.getMedia().add(cover);
        } else if (!covers.getFirst().getUrl().equals(url)) {
            covers.getFirst().setUrl(url);
            covers.getFirst().setProvider("ADMIN");
        } else {
            return;
        }
        changed.add("coverUrl");
    }

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
