package com.ltz.game.infrastructure.config;

import com.ltz.game.application.service.CatalogImportService;
import com.ltz.game.application.service.GameDetailRefreshService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CatalogSchedulingConfigurationTest {
    @Test void concurrentAdminEditConflictDoesNotStopTheTickOrBubbleUp() {
        var imports = mock(CatalogImportService.class);
        var details = mock(GameDetailRefreshService.class);
        UUID id = UUID.randomUUID();
        when(imports.synchronizeNextPage()).thenThrow(new org.springframework.orm.ObjectOptimisticLockingFailureException(com.ltz.game.domain.entity.Game.class, id));
        when(details.dueCandidates()).thenReturn(List.of(id));
        var configuration = new CatalogSchedulingConfiguration(imports, details, mock(com.ltz.game.application.service.CatalogClassificationService.class), mock(com.ltz.game.application.service.PackageReviewService.class));
        assertDoesNotThrow(configuration::synchronize);
        verify(details).refresh(id);
        // Diğer hatalar gizlenmez.
        doThrow(new IllegalStateException("beklenmeyen")).when(imports).synchronizeNextPage();
        assertThrows(IllegalStateException.class, configuration::synchronize);
    }

    @Test void schedulingRequiresExplicitOptInAndInvokesTransactionalServices() {
        var imports = mock(CatalogImportService.class);
        var details = mock(GameDetailRefreshService.class);
        var runner = new ApplicationContextRunner().withUserConfiguration(CatalogSchedulingConfiguration.class)
                .withBean(CatalogImportService.class, () -> imports).withBean(GameDetailRefreshService.class, () -> details)
                .withBean(com.ltz.game.application.service.CatalogClassificationService.class, () -> mock(com.ltz.game.application.service.CatalogClassificationService.class))
                .withBean(com.ltz.game.application.service.PackageReviewService.class, () -> mock(com.ltz.game.application.service.PackageReviewService.class));
        runner.run(context -> assertFalse(context.containsBean("catalogSchedulingConfiguration")));
        UUID id = UUID.randomUUID(); when(details.dueCandidates()).thenReturn(List.of(id));
        runner.withPropertyValues("game.catalog.sync.enabled=true").run(context -> {
            context.getBean(CatalogSchedulingConfiguration.class).synchronize();
            verify(imports).synchronizeNextPage(); verify(details).refresh(id);
        });
    }

    @Test void detailBootstrapCandidatesAreRefreshedInBatchesAndOneConflictDoesNotStopTheRest() {
        var imports = mock(CatalogImportService.class);
        var details = mock(GameDetailRefreshService.class);
        UUID first = UUID.randomUUID(), second = UUID.randomUUID();
        when(details.detailBootstrapCandidates(CatalogSchedulingConfiguration.DETAIL_BOOTSTRAP_BATCH)).thenReturn(List.of(first, second));
        doThrow(new org.springframework.orm.ObjectOptimisticLockingFailureException(com.ltz.game.domain.entity.Game.class, first)).when(details).refresh(first);
        var configuration = new CatalogSchedulingConfiguration(imports, details, mock(com.ltz.game.application.service.CatalogClassificationService.class), mock(com.ltz.game.application.service.PackageReviewService.class));
        assertDoesNotThrow(configuration::synchronize);
        verify(details).refresh(first); verify(details).refresh(second);
    }

    @Test void restartResumesAFailedSyncAndNeverPropagatesFailures() {
        var imports = mock(CatalogImportService.class);
        var configuration = new CatalogSchedulingConfiguration(imports, mock(GameDetailRefreshService.class), mock(com.ltz.game.application.service.CatalogClassificationService.class), mock(com.ltz.game.application.service.PackageReviewService.class));
        configuration.resumeAfterRestart();
        verify(imports).resumeAfterRestart();
        when(imports.resumeAfterRestart()).thenThrow(new IllegalStateException("db"));
        assertDoesNotThrow(configuration::resumeAfterRestart);
    }

    @Test void bulkClassificationRunsUpToTheStepLimitAndStopsWhenThereIsNothingToDo() {
        var classification = mock(com.ltz.game.application.service.CatalogClassificationService.class);
        when(classification.enrichNextBatch()).thenReturn(-1);
        when(classification.classifyNextBatch()).thenReturn(3);
        var configuration = new CatalogSchedulingConfiguration(mock(CatalogImportService.class), mock(GameDetailRefreshService.class), classification, mock(com.ltz.game.application.service.PackageReviewService.class));
        configuration.synchronize();
        verify(classification, times(CatalogSchedulingConfiguration.CLASSIFICATION_STEPS)).classifyNextBatch();
        var idle = mock(com.ltz.game.application.service.CatalogClassificationService.class);
        when(idle.enrichNextBatch()).thenReturn(-1);
        when(idle.classifyNextBatch()).thenReturn(-1);
        new CatalogSchedulingConfiguration(mock(CatalogImportService.class), mock(GameDetailRefreshService.class), idle, mock(com.ltz.game.application.service.PackageReviewService.class)).synchronize();
        verify(idle, times(1)).classifyNextBatch();
    }

    @Test void metadataEnrichmentRunsFirstAndClassificationWaitsUntilEnrichmentIsIdle() {
        var classification = mock(com.ltz.game.application.service.CatalogClassificationService.class);
        when(classification.enrichNextBatch()).thenReturn(5, 5, -1);
        new CatalogSchedulingConfiguration(mock(CatalogImportService.class), mock(GameDetailRefreshService.class), classification, mock(com.ltz.game.application.service.PackageReviewService.class)).synchronize();
        verify(classification, times(CatalogSchedulingConfiguration.ENRICHMENT_STEPS)).enrichNextBatch();
        verify(classification, never()).classifyNextBatch();
        // Sonraki tur: zenginleştirme bitti (-1), sınıflandırma başlar.
        when(classification.classifyNextBatch()).thenReturn(-1);
        new CatalogSchedulingConfiguration(mock(CatalogImportService.class), mock(GameDetailRefreshService.class), classification, mock(com.ltz.game.application.service.PackageReviewService.class)).synchronize();
        verify(classification).classifyNextBatch();
    }

    @Test void igdbWorkWaitsWhileTheSteamScanIsRunning() {
        var imports = mock(CatalogImportService.class);
        var classification = mock(com.ltz.game.application.service.CatalogClassificationService.class);
        var running = mock(com.ltz.game.api.dto.response.CatalogStatusResponse.class);
        when(running.syncStatus()).thenReturn(com.ltz.game.domain.enums.CatalogSyncStatus.RUNNING);
        when(imports.synchronizeNextPage()).thenReturn(running);
        new CatalogSchedulingConfiguration(imports, mock(GameDetailRefreshService.class), classification, mock(com.ltz.game.application.service.PackageReviewService.class)).synchronize();
        verify(classification, never()).enrichNextBatch();
        verify(classification, never()).classifyNextBatch();
        var complete = mock(com.ltz.game.api.dto.response.CatalogStatusResponse.class);
        when(complete.syncStatus()).thenReturn(com.ltz.game.domain.enums.CatalogSyncStatus.COMPLETE);
        when(imports.synchronizeNextPage()).thenReturn(complete);
        when(classification.enrichNextBatch()).thenReturn(-1);
        new CatalogSchedulingConfiguration(imports, mock(GameDetailRefreshService.class), classification, mock(com.ltz.game.application.service.PackageReviewService.class)).synchronize();
        verify(classification).enrichNextBatch();
        verify(classification, times(CatalogSchedulingConfiguration.CLASSIFICATION_STEPS)).classifyNextBatch();
    }
}
