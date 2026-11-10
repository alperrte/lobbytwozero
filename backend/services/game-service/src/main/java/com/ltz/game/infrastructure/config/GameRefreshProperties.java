package com.ltz.game.infrastructure.config;

import com.ltz.game.application.port.GamePriceProvider.Market;
import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Veri tazelik ayarları ({@code ltz.game.refresh.*}). Varsayılanlar geliştirme makineleri için güvenlidir (ortak Steam anahtarı, günde
 * 100.000 çağrı sınırı); canlı profil ({@code application-live.properties}) süreleri kısaltır.
 *
 * <p>"Sıcak" oyun: editör seçimi, erken erişimde ya da çıkışı son {@code hotPastDays} gün / gelecek {@code hotFutureDays} gün içinde
 * olan oyun. Sıcak oyunlar {@code hot} aralığıyla, diğerleri {@code other} aralığıyla yeniden sorulur.
 */
@ConfigurationProperties("ltz.game.refresh")
public record GameRefreshProperties(Interval release, Interval details, @DefaultValue("90") int hotPastDays,
        @DefaultValue("30") int hotFutureDays, SteamPrice steamPrice, Hints hints) {
    private static final Duration MIN_INTERVAL = Duration.ofMinutes(1);
    private static final Duration MAX_INTERVAL = Duration.ofDays(7);
    private static final String DEFAULT_STEAM_MARKETS = "US:USD,GB:GBP,DE:EUR,CA:CAD,AU:AUD,NZ:NZD,PL:PLN,BR:BRL,JP:JPY,KR:KRW,IN:INR,CN:CNY";

    /** Sıcak ve diğer oyunlar için yeniden sorma aralığı. */
    public record Interval(Duration hot, Duration other) {
        Interval withDefaults(Duration defaultHot, Duration defaultOther) {
            return new Interval(hot == null ? defaultHot : hot, other == null ? defaultOther : other);
        }
        void validate(String name) {
            if (hot.compareTo(MIN_INTERVAL) < 0 || hot.compareTo(MAX_INTERVAL) > 0 || other.compareTo(MIN_INTERVAL) < 0
                    || other.compareTo(MAX_INTERVAL) > 0 || other.compareTo(hot) < 0) {
                throw new IllegalArgumentException("Geçersiz yenileme aralığı (" + name + "): 1 dakika – 7 gün, diğerleri sıcaktan kısa olamaz.");
            }
        }
    }

    /**
     * Steam'in kendi mağaza fiyatı. {@code hotDirect}: sıcak oyunların Steam fiyatı her yenilemede doğrudan Steam'den alınır (canlı profil);
     * {@code gapFallback}: ITAD'in Steam teklifi vermediği oyunlar için Steam'den alınır; {@code markets}: Steam para biriminin pazar para
     * birimiyle aynı olduğu doğrulanmış pazarlar ("US:USD,GB:GBP" biçimi).
     */
    public record SteamPrice(Boolean hotDirect, Boolean gapFallback, String markets) { }

    /** Sayfası açılan oyunlar için bellek içi ipucu ve tahliye ayarları. */
    public record Hints(Boolean enabled, Integer maxKeys, Duration repeatWindow, Integer batch, Integer maxDrainsPerMinute) { }

    public GameRefreshProperties {
        release = (release == null ? new Interval(null, null) : release).withDefaults(Duration.ofHours(1), Duration.ofHours(24));
        details = (details == null ? new Interval(null, null) : details).withDefaults(Duration.ofHours(2), Duration.ofHours(24));
        release.validate("release");
        details.validate("details");
        if (hotPastDays < 0 || hotPastDays > 3650 || hotFutureDays < 0 || hotFutureDays > 3650) {
            throw new IllegalArgumentException("Geçersiz sıcak pencere.");
        }
        SteamPrice price = steamPrice == null ? new SteamPrice(null, null, null) : steamPrice;
        steamPrice = new SteamPrice(price.hotDirect() == null ? Boolean.FALSE : price.hotDirect(),
                price.gapFallback() == null ? Boolean.TRUE : price.gapFallback(),
                price.markets() == null || price.markets().isBlank() ? DEFAULT_STEAM_MARKETS : price.markets());
        Hints hint = hints == null ? new Hints(null, null, null, null, null) : hints;
        hints = new Hints(hint.enabled() == null ? Boolean.TRUE : hint.enabled(),
                hint.maxKeys() == null ? Integer.valueOf(5000) : hint.maxKeys(),
                hint.repeatWindow() == null ? Duration.ofMinutes(5) : hint.repeatWindow(),
                hint.batch() == null ? Integer.valueOf(100) : hint.batch(),
                hint.maxDrainsPerMinute() == null ? Integer.valueOf(6) : hint.maxDrainsPerMinute());
        if (hints.maxKeys() < 1 || hints.maxKeys() > 100_000 || hints.batch() < 1 || hints.batch() > 100
                || hints.maxDrainsPerMinute() < 1 || hints.maxDrainsPerMinute() > 60
                || hints.repeatWindow().compareTo(Duration.ofSeconds(10)) < 0 || hints.repeatWindow().compareTo(Duration.ofHours(1)) > 0) {
            throw new IllegalArgumentException("Geçersiz ipucu ayarı.");
        }
        steamMarketSet(steamPrice.markets());
    }

    /** Eski (varsayılan) kurulum: yalnız varsayılan değerler. */
    public static GameRefreshProperties defaults() { return new GameRefreshProperties(null, null, 90, 30, null, null); }

    /** Sıcak oyunların Steam fiyatı her turda doğrudan Steam'den alınır mı (koşullarda kutulu {@code Boolean} yerine ilkel değer). */
    public boolean steamHotDirect() { return Boolean.TRUE.equals(steamPrice.hotDirect()); }

    /** ITAD'in Steam teklifi vermediği oyunların fiyatı Steam'den alınır mı. */
    public boolean steamGapFallback() { return Boolean.TRUE.equals(steamPrice.gapFallback()); }

    /** Sayfası açılan oyunların ipucu kuyruğu etkin mi. */
    public boolean hintsEnabled() { return Boolean.TRUE.equals(hints.enabled()); }

    /** Steam fiyatının doğrudan alınabileceği pazarlar. */
    public Set<Market> steamMarkets() { return steamMarketSet(steamPrice.markets()); }

    private static Set<Market> steamMarketSet(String text) {
        Set<Market> all = new LinkedHashSet<>();
        for (String entry : List.of(text.split(","))) {
            String[] parts = entry.strip().split(":");
            if (parts.length != 2) throw new IllegalArgumentException("Geçersiz Steam fiyat pazarı: ülke:para birimi biçimi gerekir.");
            all.add(new Market(parts[0].strip(), parts[1].strip()));
        }
        return Set.copyOf(all);
    }
}
