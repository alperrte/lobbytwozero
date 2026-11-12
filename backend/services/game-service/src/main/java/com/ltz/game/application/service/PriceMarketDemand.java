package com.ltz.game.application.service;

import com.ltz.game.application.port.GamePriceProvider.Market;
import com.ltz.game.infrastructure.config.PriceDemandProperties;
import com.ltz.game.infrastructure.persistence.repository.GamePriceRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;
import org.springframework.stereotype.Component;

/**
 * Hangi fiyat pazarlarının arka planda yenileneceğini okuma talebine göre seçer. ITAD kotası dar olduğundan 13 pazarın hepsini sürekli yenilemek mümkün değildir
 * (saatte ≈1,25 milyon oyun-fiyatı gerekir); okunmayan pazarın yenilenmesi kotayı boşa harcar.
 *
 * <p>Okumalar bellek içi sayaçta birikir (okuma işlemi salt-okunurdur, veritabanına yazmaz) ve her yenileme turunun başında günlük sayaç tablosuna boşaltılır.
 * Etkin pazarlar: sabit pazarlar ({@link PriceDemandProperties#always()}) ve son {@code window} içinde en az {@code minReads} okuma almış, en çok okunan
 * {@code maxSecondary} pazar. Etkin olmayan pazarlar sayfa açılışında (ipucu) yine anında yenilenir ve okunmaya başlayınca kendiliğinden etkin olur.
 */
@Component
public class PriceMarketDemand {
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(PriceMarketDemand.class);
    /** Bu günden eski sayaç satırları silinir (pencere en çok 30 gündür). */
    private static final int RETENTION_DAYS = 45;

    private final GamePriceRepository repository;
    private final PriceDemandProperties properties;
    private final Clock clock;
    private final Map<Market, LongAdder> pending = new ConcurrentHashMap<>();
    private volatile LocalDate prunedOn = LocalDate.MIN;

    public PriceMarketDemand(GamePriceRepository repository, PriceDemandProperties properties, Clock clock) {
        this.repository = repository;
        this.properties = properties;
        this.clock = clock;
    }

    /** Talep sayacı kapalı bileşen: hiçbir okuma sayılmaz, tüm yapılandırılmış pazarlar arka planda yenilenir (veritabanına dokunmaz). */
    static PriceMarketDemand disabled() {
        return new PriceMarketDemand(null, new PriceDemandProperties(false, "", Duration.ofDays(7), 1, 0), Clock.systemUTC());
    }

    /**
     * Bir pazar okundu ({@code games}: yanıttaki etkin oyun sayısı; hiç oyun yoksa okuma sayılmaz). Yalnız bellek içi sayaç artar; bilinmeyen pazar sayılmaz
     * (sayaç sayısı yapılandırılmış pazar sayısıyla sınırlıdır).
     */
    public void touch(Market market, List<Market> configured, int games) {
        if (games == 0 || !properties.enabled() || market == null || !configured.contains(market)) return;
        pending.computeIfAbsent(market, key -> new LongAdder()).increment();
    }

    /** Bu turda arka planda yenilenecek pazarlar, {@code configured} sırasıyla. Etkin değilse yapılandırılmış pazarların tamamı. */
    public List<Market> activeMarkets(List<Market> configured) {
        if (!properties.enabled()) return configured;
        List<Market> always = properties.always();
        try {
            flush();
            List<Market> secondary = secondary(repository.demandMarkets(today().minusDays(properties.window().toDays()), properties.minReads()), always);
            return configured.stream().filter(market -> always.contains(market) || secondary.contains(market)).toList();
        } catch (RuntimeException error) {
            // Sayaç tablosu okunamıyorsa kotayı yememek için yalnız sabit pazarlar yenilenir; sayfa açılışı ipuçları etkilenmez.
            LOG.warn("Fiyat pazarı talebi okunamadı, yalnız sabit pazarlar yenilenecek: hata={}", error.getClass().getSimpleName());
            return configured.stream().filter(always::contains).toList();
        }
    }

    /** En çok okunan, sabit olmayan pazarlar (en çok {@code maxSecondary}). */
    private List<Market> secondary(List<Market> demanded, List<Market> always) {
        return demanded.stream().filter(market -> !always.contains(market)).limit(properties.maxSecondary()).toList();
    }

    /** Birikmiş okumaları günlük sayaç tablosuna yazar; yazılamayan sayılar kaybolmaz, sonraki tura kalır. Eski satırlar günde bir kez silinir. */
    void flush() {
        Map<Market, Long> taken = new HashMap<>();
        pending.forEach((market, adder) -> {
            long count = adder.sumThenReset();
            if (count > 0) taken.put(market, count);
        });
        if (!taken.isEmpty()) {
            try {
                repository.recordDemand(taken, today());
            } catch (RuntimeException error) {
                taken.forEach((market, count) -> pending.computeIfAbsent(market, key -> new LongAdder()).add(count));
                throw error;
            }
        }
        LocalDate today = today();
        if (prunedOn.isBefore(today)) {
            repository.pruneDemand(today.minusDays(RETENTION_DAYS));
            prunedOn = today;
        }
    }

    private LocalDate today() { return LocalDate.now(clock.withZone(ZoneOffset.UTC)); }
}
