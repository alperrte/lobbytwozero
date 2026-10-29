package com.ltz.game.application.service;
import com.ltz.game.domain.entity.Game;
import com.ltz.game.domain.entity.GameLanguageSupport;
import com.ltz.game.domain.enums.LanguageSupportStatus;
import com.ltz.game.domain.enums.MetadataDataState;
import com.ltz.game.domain.exception.InvalidGameRequestException;
import java.text.Normalizer;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
@Service @RequiredArgsConstructor
public class LocalizationService {
    private final MetadataProvenanceService provenance;
    public boolean apply(Game game, LocalizationEdit edit) {
        if (edit == null) return false;
        RequestRules.reason(edit.evidence());
        String locale = edit.metadataLanguage()==null ? game.getMetadataLanguage() : normalizedLocale(edit.metadataLanguage());
        Set<GameLanguageSupport> supports = edit.languageSupport()==null ? new HashSet<>(game.getLanguageSupport()) : supports(edit.languageSupport());
        MetadataDataState state = resolveState(game, edit, supports);
        validateState(state, supports, edit.verified());
        boolean changed = !Objects.equals(locale,game.getMetadataLanguage()) || state!=game.getLanguageDataState()
                || !supports.equals(game.getLanguageSupport()) || edit.verified();
        if (!changed) return false;
        game.setMetadataLanguage(locale);
        game.setLanguageDataState(state);
        game.getLanguageSupport().clear(); game.getLanguageSupport().addAll(supports);
        provenance.recordObservation(game,"LOCALIZATION","ADMIN",state,edit.verified());
        return true;
    }
    private MetadataDataState resolveState(Game game, LocalizationEdit edit, Set<GameLanguageSupport> supports) {
        MetadataDataState state = edit.dataState()==null ? game.getLanguageDataState() : edit.dataState();
        if (edit.dataState()==null && edit.languageSupport()!=null)
            state = supports.isEmpty() ? MetadataDataState.EMPTY : MetadataDataState.AVAILABLE;
        return state;
    }
    private void validateState(MetadataDataState state, Set<GameLanguageSupport> supports, boolean verified) {
        if ((state==MetadataDataState.AVAILABLE) != !supports.isEmpty()) throw invalid();
        if (verified && (state==MetadataDataState.UNKNOWN || state==MetadataDataState.INVALID)) throw invalid();
    }
    private Set<GameLanguageSupport> supports(List<LocalizationEdit.Support> inputs) {
        if (inputs.size()>100) throw invalid();
        Set<String> codes=new HashSet<>();
        Set<GameLanguageSupport> values=new HashSet<>();
        for (var input: inputs) {
            if (input==null) throw invalid();
            String code=languageCode(input.languageCode());
            if (!codes.add(code)) throw invalid();
            var value=new GameLanguageSupport(); value.setLanguageCode(code);
            value.setInterfaceSupport(unknown(input.interfaceSupport()));
            value.setAudioSupport(unknown(input.audioSupport()));
            value.setSubtitleSupport(unknown(input.subtitleSupport())); values.add(value);
        }
        return values;
    }
    private LanguageSupportStatus unknown(LanguageSupportStatus value) { return value==null ? LanguageSupportStatus.UNKNOWN : value; }
    private String normalizedLocale(String raw) { return raw.isBlank() ? null : languageCode(raw); }
    public static String languageCode(String raw) {
        if (raw==null) throw invalid();
        String code=Normalizer.normalize(raw,Normalizer.Form.NFKC).strip().toLowerCase(Locale.ROOT);
        if (Set.of("türkçe","turkce","turkish").contains(code)) code="tr";
        if (!code.matches("[a-z]{2,3}(-[a-z0-9]{2,8}){0,3}")) throw invalid();
        return code;
    }
    private static InvalidGameRequestException invalid() { return new InvalidGameRequestException("INVALID_LOCALIZATION"); }
}
