package com.ltz.game.infrastructure.config;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ltz.game.application.service.EmptyDescriptionService;
import com.ltz.game.application.service.GameDetailBulkService;
import com.ltz.game.application.service.LocalizedDescriptionService;
import com.ltz.game.application.service.SteamReleaseStatusService;
import com.ltz.game.application.service.SteamStoreEnrichmentService;
import com.ltz.game.application.service.SteamStoreItemService;
import com.ltz.game.application.service.SteamTypeClassificationService;
import org.junit.jupiter.api.Test;
import org.springframework.dao.CannotAcquireLockException;

/** Arka plan zenginleştirme turları: kilit çakışması (deadlock kurbanı) ve eşzamanlı değişiklik turu düşürmez; diğer hatalar gizlenmez. */
class CatalogEnrichmentSchedulingConfigurationTest {
    private final GameDetailBulkService bulk = mock(GameDetailBulkService.class);
    private final SteamTypeClassificationService types = mock(SteamTypeClassificationService.class);
    private final SteamReleaseStatusService status = mock(SteamReleaseStatusService.class);
    private final CatalogEnrichmentSchedulingConfiguration configuration = new CatalogEnrichmentSchedulingConfiguration(bulk,
            mock(SteamStoreEnrichmentService.class), mock(SteamStoreItemService.class), mock(LocalizedDescriptionService.class),
            mock(EmptyDescriptionService.class), status, types);

    @Test void aDeadlockVictimIsRetriedOnTheNextRoundInsteadOfBubblingUp() {
        when(types.nextBatch()).thenThrow(new CannotAcquireLockException("deadlock"));
        when(bulk.bootstrapNextBatch()).thenThrow(new CannotAcquireLockException("deadlock"));
        when(status.nextBatch()).thenThrow(new CannotAcquireLockException("deadlock"));
        assertDoesNotThrow(configuration::steamTypeClassification);
        assertDoesNotThrow(configuration::bulkDetails);
        assertDoesNotThrow(configuration::steamReleaseStatus);
        verify(types).nextBatch();
    }

    @Test void otherFailuresAreNotHidden() {
        when(types.nextBatch()).thenThrow(new IllegalStateException("beklenmeyen"));
        assertThrows(IllegalStateException.class, configuration::steamTypeClassification);
    }
}
