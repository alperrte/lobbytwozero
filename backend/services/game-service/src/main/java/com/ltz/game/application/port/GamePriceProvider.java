package com.ltz.game.application.port;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
/** GAME-24 hazırlığı: izinli sağlayıcı onaylanmadan adaptör/bean/ağ çağrısı yoktur. */
public interface GamePriceProvider {
    /** Çoklu kimlik tek çağrıda; sağlayıcı kimliği ile LTZ gameId doğrulanmış eşleme gerektirir. */
    List<Quote> prices(Set<UUID> gameIds, Market market);
    record Market(String country, String currency) {
        public Market {
            if (country==null || !country.matches("[A-Z]{2}") || !Set.of(Locale.getISOCountries()).contains(country)) throw invalid();
            try { Currency.getInstance(currency); }
            catch (RuntimeException _) { throw invalid(); }
        }
    }
    /** Fiyatlar aynı mağaza/ülke/para birimine aittir; snapshot fiyatı evrensel veya süresiz güncel değildir. */
    record Quote(UUID gameId, String store, Market market, BigDecimal regularPrice, BigDecimal currentPrice,
                 String source, Instant fetchedAt, Instant expiresAt) {
        public Quote {
            if (gameId==null || market==null || store==null || store.isBlank() || store.length()>64
                    || source==null || !source.matches("[A-Z][A-Z0-9_]{0,31}") || regularPrice==null || currentPrice==null
                    || regularPrice.signum()<0 || currentPrice.signum()<0 || currentPrice.compareTo(regularPrice)>0
                    || fetchedAt==null || expiresAt==null || !expiresAt.isAfter(fetchedAt)) throw invalid();
        }
        public boolean isFreshAt(Instant now) { return !now.isBefore(fetchedAt) && now.isBefore(expiresAt); }
        public boolean isDiscounted() { return currentPrice.compareTo(regularPrice)<0; }
    }
    private static IllegalArgumentException invalid() { return new IllegalArgumentException("Geçersiz bölgesel fiyat verisi."); }
}
