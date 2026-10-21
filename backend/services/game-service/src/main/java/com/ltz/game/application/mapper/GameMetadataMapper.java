package com.ltz.game.application.mapper;
import com.ltz.game.api.dto.response.GameMetadataResponse;
import com.ltz.game.domain.entity.Game;
import com.ltz.game.domain.enums.TermKind;
import org.springframework.stereotype.Component;
import java.util.*;
@Component
public class GameMetadataMapper {
    public GameMetadataResponse toResponse(Game game) {
        return new GameMetadataResponse(game.getId(), terms(game, TermKind.GENRE), terms(game, TermKind.CATEGORY),
                terms(game, TermKind.TAG), terms(game, TermKind.PLATFORM), game.getDevelopers().stream().sorted().toList(),
                game.getPublishers().stream().sorted().toList(), game.getMedia().stream()
                .sorted(Comparator.comparingInt(com.ltz.game.domain.entity.GameMedia::getSortOrder)
                        .thenComparing(com.ltz.game.domain.entity.GameMedia::getId))
                .map(m -> new GameMetadataResponse.Media(m.getType(), m.getUrl(), m.getProvider(), m.getSortOrder())).toList(),
                game.getSystemRequirements().stream().sorted(Comparator
                        .comparing(com.ltz.game.domain.entity.GameSystemRequirement::getPlatform)
                        .thenComparing(com.ltz.game.domain.entity.GameSystemRequirement::getLevel))
                .map(r -> new GameMetadataResponse.Requirement(r.getLevel(), r.getPlatform(), r.getOs(), r.getCpu(),
                        r.getGpu(), r.getRamMb(), r.getStorageMb(), r.getRawText())).toList(),
                game.getLanguages().stream().sorted().toList(), game.getEarlyAccess(), game.getMetadataLanguage(), game.getLanguageDataState(),
                game.getLanguageSupport().stream().sorted(Comparator.comparing(com.ltz.game.domain.entity.GameLanguageSupport::getLanguageCode))
                        .map(s -> new GameMetadataResponse.LanguageSupport(s.getLanguageCode(),s.getInterfaceSupport(),s.getAudioSupport(),s.getSubtitleSupport())).toList(),
                game.getMetadataProvenance().entrySet().stream().sorted(Map.Entry.comparingByKey())
                        .map(e -> new GameMetadataResponse.Provenance(e.getKey(),e.getValue().getSource(),e.getValue().getDataState(),e.getValue().getRecordedAt(),e.getValue().getVerifiedAt())).toList());
    }
    private List<GameMetadataResponse.Term> terms(Game game, TermKind kind) {
        return game.getTerms().stream().filter(t -> t.getKind() == kind)
                .sorted(Comparator.comparing(com.ltz.game.domain.entity.CatalogTerm::getCode))
                .map(t -> new GameMetadataResponse.Term(t.getId(), t.getCode(), t.getNameTr(), t.getNameEn())).toList();
    }
}
