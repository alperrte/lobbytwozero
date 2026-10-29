package com.ltz.game.application.service;
import com.ltz.game.domain.entity.Game;
import com.ltz.game.domain.enums.LanguageSupportStatus;
import com.ltz.game.domain.enums.MetadataDataState;
import com.ltz.game.domain.exception.InvalidGameRequestException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
class LocalizationServiceTest {
    final LocalizationService service=new LocalizationService(new MetadataProvenanceService(Clock.fixed(Instant.EPOCH,ZoneOffset.UTC)));
    @Test void turkishNamesAndTagsNormalizeWithoutGuessingSupport() {
        for(String label:List.of("Türkçe","Turkish"," TURKCE ","ＴＲ")) assertEquals("tr",LocalizationService.languageCode(label));
        var game=new Game();
        var edit=new LocalizationEdit("tr",null,List.of(new LocalizationEdit.Support("Turkish",null,null,null)),false,"Dil kaynağı manuel olarak kontrol edildi");
        assertTrue(service.apply(game,edit));
        var support=game.getLanguageSupport().iterator().next();
        assertEquals(LanguageSupportStatus.UNKNOWN,support.getInterfaceSupport());
        assertEquals(LanguageSupportStatus.UNKNOWN,support.getAudioSupport());
        assertEquals(LanguageSupportStatus.UNKNOWN,support.getSubtitleSupport());
        assertFalse(service.apply(game,edit));
        assertFalse(service.apply(game,null));
    }
    @Test void wrongSourceTextAndDuplicateCodesAreRejectedRatherThanMappedToFalse() {
        for(String label:List.of("","Turkish*","<b>Türkçe</b>","not a language"))
            assertThrows(InvalidGameRequestException.class,()->LocalizationService.languageCode(label));
        var edit=new LocalizationEdit(null,null,List.of(new LocalizationEdit.Support("tr",null,null,null),new LocalizationEdit.Support("Turkish",null,null,null)),false,"Mükerrer kaynak kodu kontrol edildi");
        var game=new Game();
        assertThrows(InvalidGameRequestException.class,()->service.apply(game,edit));
        assertTrue(game.getLanguageSupport().isEmpty());
    }
    @Test void invalidStateDoesNotApplyCandidateLocaleOrProvenance() {
        for (var state : List.of(MetadataDataState.AVAILABLE, MetadataDataState.UNKNOWN, MetadataDataState.INVALID)) {
            var game = new Game();
            game.setMetadataLanguage("en");
            var edit = new LocalizationEdit("tr", state, List.of(), true, "Dil desteği kaynağı kontrol edildi");
            var error = assertThrows(InvalidGameRequestException.class, () -> service.apply(game, edit));
            assertEquals("INVALID_LOCALIZATION", error.code());
            assertEquals("en", game.getMetadataLanguage());
            assertTrue(game.getLanguageSupport().isEmpty());
            assertTrue(game.getMetadataProvenance().isEmpty());
        }
    }
    @Test void verifiedEmptyStateRemainsAnObservationEvenWhenRepeated() {
        var game = new Game();
        var edit = new LocalizationEdit(null, MetadataDataState.EMPTY, List.of(), true, "Boş dil desteği doğrulandı");
        assertTrue(service.apply(game, edit));
        assertTrue(service.apply(game, edit));
        assertEquals(MetadataDataState.EMPTY, game.getLanguageDataState());
        assertEquals(Instant.EPOCH, game.getMetadataProvenance().get("LOCALIZATION").getVerifiedAt());
    }
}
