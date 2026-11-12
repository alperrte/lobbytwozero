package com.ltz.game.infrastructure.config;

import com.ltz.game.application.port.GamePriceProvider.Market;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Talebe göre fiyat pazarı ({@code ltz.game.pricing.demand.*}). ITAD kotası dar olduğundan yapılandırılmış 13 pazarın hepsi sürekli yenilenmez:
 * {@code alwaysMarkets} her zaman, diğerleri yalnız son {@code window} içinde en az {@code minReads} okuma almışsa (en çok {@code maxSecondary} tanesi)
 * arka planda yenilenir. Diğer pazarlar sayfa açılışında yine anında yenilenir. {@code enabled=false} tüm pazarların yenilendiği eski davranıştır (geri alma anahtarı).
 *
 * <p>{@code maxSecondary}, herkese açık fiyat okuma ucunun kötüye kullanımına karşı üst sınırdır: en kötü durumda sabit pazarlara bu kadar pazar eklenir.
 */
@ConfigurationProperties("ltz.game.pricing.demand")
public record PriceDemandProperties(@DefaultValue("true") boolean enabled, @DefaultValue("US:USD,TR:TRY") String alwaysMarkets, @DefaultValue("7d") Duration window,
        @DefaultValue("3") int minReads, @DefaultValue("3") int maxSecondary) {
    @ConstructorBinding
    public PriceDemandProperties {
        if (window == null || window.compareTo(Duration.ofDays(1)) < 0 || window.compareTo(Duration.ofDays(30)) > 0)
            throw new IllegalArgumentException("Pazar talep penceresi 1 – 30 gün olmalıdır.");
        if (minReads < 1) throw new IllegalArgumentException("En az okuma sayısı 1 veya daha fazla olmalıdır.");
        if (maxSecondary < 0 || maxSecondary > 12) throw new IllegalArgumentException("Talebe bağlı pazar sayısı 0 – 12 olmalıdır.");
        always(alwaysMarkets);
    }

    /** Varsayılanlar (testler ve basit kurulumlar). */
    public static PriceDemandProperties defaults() { return new PriceDemandProperties(true, "US:USD,TR:TRY", Duration.ofDays(7), 3, 3); }

    /** Her zaman yenilenen pazarlar ("US:USD,TR:TRY" biçimi; her biri {@link Market} kurallarıyla doğrulanır). */
    public List<Market> always() { return always(alwaysMarkets); }

    private static List<Market> always(String value) {
        List<Market> markets = new ArrayList<>();
        if (value == null || value.isBlank()) return markets;
        for (String entry : value.split(",")) {
            String[] parts = entry.strip().split(":");
            if (parts.length != 2) throw new IllegalArgumentException("Geçersiz sabit fiyat pazarı: ülke:para birimi biçimi gerekir.");
            Market market = new Market(parts[0].strip(), parts[1].strip());
            if (!markets.contains(market)) markets.add(market);
        }
        return markets;
    }
}
