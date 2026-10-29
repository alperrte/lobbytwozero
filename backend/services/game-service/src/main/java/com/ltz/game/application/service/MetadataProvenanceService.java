package com.ltz.game.application.service;
import com.ltz.game.domain.entity.Game;
import com.ltz.game.domain.entity.GameMetadataProvenance;
import com.ltz.game.domain.enums.MetadataDataState;
import java.time.Clock;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
/** Kayıt zamanı dış kaynak doğrulaması değildir; verified yalnız açık doğrulama işlemi içindir. */
@Service @RequiredArgsConstructor
public class MetadataProvenanceService {
    private static final java.util.regex.Pattern FIELD_BOUNDARY=java.util.regex.Pattern.compile("([a-z])([A-Z])");
    private final Clock clock;
    public void adminChanges(Game game, List<String> fields) {
        for (String field: fields) {
            if (field.equals("localization")) continue;
            MetadataDataState state = fieldState(game, field);
            recordObservation(game,FIELD_BOUNDARY.matcher(field).replaceAll("$1_$2").toUpperCase(java.util.Locale.ROOT),"ADMIN",state,false);
        }
    }
    private MetadataDataState fieldState(Game game, String field) {
        boolean empty = switch(field) {
            case "description" -> game.getDescription()==null;
            case "developers" -> game.getDevelopers().isEmpty();
            case "publishers" -> game.getPublishers().isEmpty();
            case "languages" -> game.getLanguages().isEmpty();
            case "coverUrl" -> game.getMedia().stream().noneMatch(media -> media.getType()==com.ltz.game.domain.enums.MediaType.COVER);
            case "genres" -> noTerms(game,com.ltz.game.domain.enums.TermKind.GENRE);
            case "categories" -> noTerms(game,com.ltz.game.domain.enums.TermKind.CATEGORY);
            case "tags" -> noTerms(game,com.ltz.game.domain.enums.TermKind.TAG);
            case "platforms" -> noTerms(game,com.ltz.game.domain.enums.TermKind.PLATFORM);
            default -> false;
        };
        MetadataDataState state=empty ? MetadataDataState.EMPTY : MetadataDataState.AVAILABLE;
        if (field.equals("gameType") && game.getGameType()==com.ltz.game.domain.enums.GameType.UNKNOWN) state=MetadataDataState.UNKNOWN;
        return state;
    }
    private boolean noTerms(Game game, com.ltz.game.domain.enums.TermKind kind) {
        return game.getTerms().stream().noneMatch(term -> term.getKind()==kind);
    }
    public void recordObservation(Game game, String field, String source, MetadataDataState state, boolean verified) {
        var provenance = new GameMetadataProvenance();
        provenance.setSource(source);
        provenance.setDataState(state);
        provenance.setRecordedAt(clock.instant());
        provenance.setVerifiedAt(verified ? provenance.getRecordedAt() : null);
        game.getMetadataProvenance().put(field,provenance);
    }
}
