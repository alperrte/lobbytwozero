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
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(CatalogSchedulingConfiguration.class);

    @Scheduled(fixedDelay = 5000, initialDelay = 30000)
    public void synchronize() {
        try {
            imports.synchronizeNextPage();
        } catch (org.springframework.orm.ObjectOptimisticLockingFailureException _) {
            // Aynı oyun aynı anda yönetici tarafından değiştirildi: sayfa geri alındı, checkpoint ilerlemedi ve veri kaybı yok.
            // Bir sonraki turda sayfa yeniden denenir; bu bir hata değildir, bu yüzden yığın izi basılmaz.
            LOG.warn("Katalog sayfası eşzamanlı oyun değişikliği nedeniyle geri alındı; sonraki turda yeniden denenecek.");
        }
        for (var id : details.dueCandidates()) details.refresh(id);
    }
}
