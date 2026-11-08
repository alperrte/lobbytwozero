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
    private final com.ltz.game.application.service.CatalogClassificationService classification;
    private final com.ltz.game.application.service.PackageReviewService packageReview;
    public CatalogSchedulingConfiguration(CatalogImportService imports, com.ltz.game.application.service.GameDetailRefreshService details,
            com.ltz.game.application.service.CatalogClassificationService classification,
            com.ltz.game.application.service.PackageReviewService packageReview) {
        this.imports = imports;
        this.details = details;
        this.classification = classification;
        this.packageReview = packageReview;
    }
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(CatalogSchedulingConfiguration.class);

    /** Tur başına en çok bu kadar toplu sınıflandırma adımı (adım başına 250 AppID; IGDB hız sınırı kaynakta ayrıca uygulanır). */
    static final int CLASSIFICATION_STEPS = 4;
    /** Tur başına en çok bu kadar metadata zenginleştirme adımı (türü doğrulanmış kayıtlar; sınıflandırmadan önce biter). */
    static final int ENRICHMENT_STEPS = 2;
    /** Tur başına en çok bu kadar PACKAGE inceleme adımı (adım başına 250 kayıt; Steam DLC kanıtı varsa IGDB sorulur). */
    static final int PACKAGE_REVIEW_STEPS = 2;
    /** Tur başına, ana oyun olarak sınıflanmış ve detayı hiç denenmemiş en çok bu kadar kayıt için detay alınır. */
    static final int DETAIL_BOOTSTRAP_BATCH = 10;

    /** Yeniden başlatmada FAILED aktarım cursor korunarak sürdürülür; zamanlanmış turlar bundan sonra çalışır. */
    @org.springframework.context.event.EventListener(org.springframework.boot.context.event.ApplicationReadyEvent.class)
    public void resumeAfterRestart() {
        try {
            imports.resumeAfterRestart();
        } catch (RuntimeException error) {
            LOG.warn("Başarısız aktarım yeniden başlatmada sürdürülemedi: {}", error.getClass().getSimpleName());
        }
    }

    @Scheduled(fixedDelay = 5000, initialDelay = 30000)
    public void synchronize() {
        com.ltz.game.api.dto.response.CatalogStatusResponse status = null;
        try {
            status = imports.synchronizeNextPage();
        } catch (org.springframework.orm.ObjectOptimisticLockingFailureException _) {
            // Aynı oyun aynı anda yönetici tarafından değiştirildi: sayfa geri alındı, checkpoint ilerlemedi ve veri kaybı yok.
            // Bir sonraki turda sayfa yeniden denenir; bu bir hata değildir, bu yüzden yığın izi basılmaz.
            LOG.warn("Katalog sayfası eşzamanlı oyun değişikliği nedeniyle geri alındı; sonraki turda yeniden denenecek.");
        }
        for (var id : details.dueCandidates()) refresh(id);
        // Steam taraması sürerken IGDB işleri bekler: iki büyük iş aynı anda çalışmaz, taramanın getirdiği yeni kayıtlar da
        // tarama bitince tek turda ele alınır.
        boolean steamScanRunning = status != null && status.syncStatus() == com.ltz.game.domain.enums.CatalogSyncStatus.RUNNING;
        if (!steamScanRunning) runIgdbWork();
        for (var id : details.detailBootstrapCandidates(DETAIL_BOOTSTRAP_BATCH)) refresh(id);
    }

    /** Sıra: önce mevcut kayıtların metadata zenginleştirmesi, o bitince türü belirsiz kayıtların sınıflandırması. */
    private void runIgdbWork() {
        boolean enriching = false;
        for (int step = 0; step < ENRICHMENT_STEPS; step++) {
            if (classification.enrichNextBatch() < 0) break;
            enriching = true;
        }
        if (enriching) return;
        for (int step = 0; step < CLASSIFICATION_STEPS && classification.classifyNextBatch() >= 0; step++) {
            // -1: yapılacak iş yok / duraklatıldı; aksi halde sıradaki toplu adım.
        }
        // PACKAGE kayıtlarının Steam DLC + IGDB kanıtıyla incelenmesi: yeni PACKAGE kalmadığında tek sorguluk boş tur.
        for (int step = 0; step < PACKAGE_REVIEW_STEPS && packageReview.reviewNextBatch() >= 0; step++) {
            // -1: incelenecek kayıt yok / duraklatıldı.
        }
    }

    private void refresh(java.util.UUID id) {
        try {
            details.refresh(id);
        } catch (org.springframework.orm.ObjectOptimisticLockingFailureException _) {
            LOG.warn("Oyun detayı eşzamanlı değişiklik nedeniyle atlandı; sonraki turda yeniden denenecek.");
        }
    }
}
