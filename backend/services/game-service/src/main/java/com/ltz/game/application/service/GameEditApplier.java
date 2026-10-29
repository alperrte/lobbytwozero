package com.ltz.game.application.service;

import com.ltz.game.domain.entity.CatalogTerm;
import com.ltz.game.domain.entity.Game;
import com.ltz.game.domain.entity.GameMedia;
import com.ltz.game.domain.enums.DeveloperMediaType;
import com.ltz.game.domain.enums.MediaType;
import com.ltz.game.domain.enums.OverridableField;
import com.ltz.game.domain.enums.TermKind;
import com.ltz.game.domain.exception.InvalidGameRequestException;
import com.ltz.game.infrastructure.persistence.repository.CatalogTermRepository;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Alan düzenlemelerini çağıran AdminGameService transaction'ı içinde uygular. */
@Component
@RequiredArgsConstructor
class GameEditApplier {
    private static final int MAX_NAMES = 100;
    private final CatalogTermRepository terms;
    private final MediaUrlPolicy urls;
    private final LocalizationService localization;

    void apply(Game game, GameEdit edit, List<String> changed, Set<OverridableField> manual) {
        applyTexts(game, edit, changed, manual);
        applyScalars(game, edit, changed, manual);
        applyCollections(game, edit, changed, manual);
        if (edit.coverUrl() != null) applyCover(game, edit.coverUrl(), changed);
        if (localization.apply(game,edit.localization())) mark(changed,"localization",manual,OverridableField.LOCALIZATION);
    }

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

}
