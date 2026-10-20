package com.ltz.game.infrastructure.config;

import com.ltz.game.application.service.CatalogImportService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/** Açıkça etkinleştirilmeden dış istek veya toplu aktarım başlatmaz. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "game.catalog.sync.enabled", havingValue = "true")
@EnableScheduling
public class CatalogSchedulingConfiguration {
    private final CatalogImportService imports;
    private final com.ltz.game.application.service.GameDetailRefreshService details;
    public CatalogSchedulingConfiguration(CatalogImportService imports, com.ltz.game.application.service.GameDetailRefreshService details) {
        this.imports = imports;
        this.details = details;
    }
    @Scheduled(fixedDelay = 5000, initialDelay = 30000)
    public void synchronize() {
        imports.synchronizeNextPage();
        for (var id : details.dueCandidates()) details.refresh(id);
    }
}
