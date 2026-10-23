package com.ltz.game.application.service;

import com.ltz.game.domain.entity.Game;
import com.ltz.game.domain.enums.GameType;
import com.ltz.game.domain.enums.MetadataDataState;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MetadataProvenanceServiceTest {
    private final MetadataProvenanceService service=new MetadataProvenanceService(Clock.fixed(Instant.EPOCH,ZoneOffset.UTC));

    @Test void unknownGameTypeIsNotAvailableAndAdminEditingIsNotVerification() {
        Game game=new Game();
        game.setGameType(GameType.MAIN_GAME);
        service.adminChanges(game,List.of("gameType"));
        assertEquals(MetadataDataState.AVAILABLE,game.getMetadataProvenance().get("GAME_TYPE").getDataState());
        game.setGameType(GameType.UNKNOWN);
        service.adminChanges(game,List.of("gameType"));
        var observation=game.getMetadataProvenance().get("GAME_TYPE");
        assertEquals(MetadataDataState.UNKNOWN,observation.getDataState());
        assertEquals("ADMIN",observation.getSource());
        assertEquals(Instant.EPOCH,observation.getRecordedAt());
        assertNull(observation.getVerifiedAt());
    }

    @Test void clearedCollectionsHaveEmptyProvenanceAndLocalizationVerificationIsPreservedSeparately() {
        Game game=new Game();
        service.recordObservation(game,"LOCALIZATION","ADMIN",MetadataDataState.AVAILABLE,true);
        service.adminChanges(game,List.of("description","developers","publishers","languages","coverUrl",
                "genres","categories","tags","platforms","localization"));
        for (String field:List.of("DESCRIPTION","DEVELOPERS","PUBLISHERS","LANGUAGES","COVER_URL","GENRES","CATEGORIES","TAGS","PLATFORMS")) {
            assertEquals(MetadataDataState.EMPTY,game.getMetadataProvenance().get(field).getDataState());
            assertNull(game.getMetadataProvenance().get(field).getVerifiedAt());
        }
        assertEquals(Instant.EPOCH,game.getMetadataProvenance().get("LOCALIZATION").getVerifiedAt());
    }
}
