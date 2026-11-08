package com.ltz.game.infrastructure.config;

import com.ltz.game.application.service.GameDetailBulkService;
import com.ltz.game.application.service.EmptyDescriptionService;
import com.ltz.game.application.service.LocalizedDescriptionService;
import com.ltz.game.application.service.SteamStoreItemService;
import com.ltz.game.application.service.SteamStoreEnrichmentService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Katalog kayıtlarının eksik detay alanlarını (kapak, geliştirici, yayıncı, dil) tamamlayan arka plan işleri. Katalog zamanlayıcısıyla
 * aynı anahtarla açılır ({@code game.catalog.sync.enabled}); ayrı iş parçacıklarında çalışır, böylece Steam aktarımı ve IGDB
 * sınıflandırması bu işlerin bekleme sürelerinden etkilenmez.
 * <ul>
 *   <li>Toplu detay: IGDB'den 100 oyunluk partiler (tek kayıt yoluna göre ~100 kat hızlı);</li>
 *   <li>Mağaza tamamlama: IGDB'nin bırakıp gittiği boşlukları Steam mağaza verisiyle doldurur (aralıklı, tek AppID).</li>
 * </ul>
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "game.catalog.sync.enabled", havingValue = "true")
@EnableScheduling
public class CatalogEnrichmentSchedulingConfiguration {
    /** Tur başına en çok bu kadar toplu detay adımı. */
    static final int BULK_DETAIL_STEPS = 3;
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(CatalogEnrichmentSchedulingConfiguration.class);

    private final GameDetailBulkService bulk;
    private final SteamStoreEnrichmentService store;
    private final SteamStoreItemService headers;
    private final LocalizedDescriptionService localized;
    private final EmptyDescriptionService emptyDescriptions;

    public CatalogEnrichmentSchedulingConfiguration(GameDetailBulkService bulk, SteamStoreEnrichmentService store, SteamStoreItemService headers,
            LocalizedDescriptionService localized, EmptyDescriptionService emptyDescriptions) {
        this.bulk = bulk;
        this.store = store;
        this.headers = headers;
        this.localized = localized;
        this.emptyDescriptions = emptyDescriptions;
    }

    /** Açıklaması boş oyunlar için Steam İngilizce kısa açıklama yedeği; kararlı durumda boş tur. */
    @Scheduled(fixedDelay = 3000, initialDelay = 70000)
    public void emptyDescriptions() {
        try {
            emptyDescriptions.nextBatch();
        } catch (org.springframework.orm.ObjectOptimisticLockingFailureException _) {
            LOG.warn("Boş açıklama partisi eşzamanlı değişiklik nedeniyle geri alındı; sonraki turda yeniden denenecek.");
        }
    }

    /** Yerelleştirilmiş açıklamalar (yapılandırılmış ek diller; varsayılan yalnız tr): iki dilli toplu istek, kararlı durumda boş tur. */
    @Scheduled(fixedDelay = 800, initialDelay = 60000)
    public void localizedDescriptions() {
        try {
            localized.nextBatch();
        } catch (org.springframework.orm.ObjectOptimisticLockingFailureException _) {
            LOG.warn("Yerelleştirilmiş açıklama partisi eşzamanlı değişiklik nedeniyle geri alındı; sonraki turda yeniden denenecek.");
        }
    }

    /** Steam toplu mağaza öğeleri (başlık görseli, geliştirici, yayıncı, dil): 100 oyun/istek; kararlı durumda boş turdur. */
    @Scheduled(fixedDelay = 1500, initialDelay = 50000)
    public void steamStoreItems() {
        try {
            headers.nextBatch();
        } catch (org.springframework.orm.ObjectOptimisticLockingFailureException _) {
            LOG.warn("Mağaza öğesi partisi eşzamanlı değişiklik nedeniyle geri alındı; sonraki turda yeniden denenecek.");
        }
    }

    @Scheduled(fixedDelay = 4000, initialDelay = 40000)
    public void bulkDetails() {
        try {
            for (int step = 0; step < BULK_DETAIL_STEPS && bulk.bootstrapNextBatch() >= 0; step++) {
                // -1: iş yok / duraklatıldı.
            }
        } catch (org.springframework.orm.ObjectOptimisticLockingFailureException _) {
            LOG.warn("Toplu detay partisi eşzamanlı değişiklik nedeniyle geri alındı; sonraki turda yeniden denenecek.");
        }
    }

    /** İstemci istekleri kendi içinde aralıklar (varsayılan 1,7 sn); bu yöntem her çağrıda en çok bir AppID işler. */
    @Scheduled(fixedDelay = 500, initialDelay = 45000)
    public void storeEnrichment() {
        try {
            store.enrichNext();
        } catch (org.springframework.orm.ObjectOptimisticLockingFailureException _) {
            LOG.warn("Mağaza tamamlama kaydı eşzamanlı değişiklik nedeniyle atlandı; sonraki turda yeniden denenecek.");
        }
    }
}
