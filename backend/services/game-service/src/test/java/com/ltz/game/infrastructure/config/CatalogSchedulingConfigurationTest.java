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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CatalogSchedulingConfigurationTest {
    @Test void concurrentAdminEditConflictDoesNotStopTheTickOrBubbleUp() {
        var imports = mock(CatalogImportService.class);
        var details = mock(GameDetailRefreshService.class);
        UUID id = UUID.randomUUID();
        when(imports.synchronizeNextPage()).thenThrow(new org.springframework.orm.ObjectOptimisticLockingFailureException(com.ltz.game.domain.entity.Game.class, id));
        when(details.dueCandidates()).thenReturn(List.of(id));
        var configuration = new CatalogSchedulingConfiguration(imports, details);
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
                .withBean(CatalogImportService.class, () -> imports).withBean(GameDetailRefreshService.class, () -> details);
        runner.run(context -> assertFalse(context.containsBean("catalogSchedulingConfiguration")));
        UUID id = UUID.randomUUID(); when(details.dueCandidates()).thenReturn(List.of(id));
        runner.withPropertyValues("game.catalog.sync.enabled=true").run(context -> {
            context.getBean(CatalogSchedulingConfiguration.class).synchronize();
            verify(imports).synchronizeNextPage(); verify(details).refresh(id);
        });
    }
}
