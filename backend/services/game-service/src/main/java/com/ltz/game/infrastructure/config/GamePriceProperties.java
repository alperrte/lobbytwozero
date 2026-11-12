package com.ltz.game.infrastructure.config;

import com.ltz.game.application.port.GamePriceProvider.Market;
import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("ltz.game.pricing")
public record GamePriceProperties(@DefaultValue("") String apiKey,
        @DefaultValue("https://api.isthereanydeal.com") String apiUrl,
        @DefaultValue("US") String country, @DefaultValue("USD") String currency,
        @DefaultValue("15m") Duration ttl,
        @DefaultValue("") String markets,
        @DefaultValue("3") int displayGrace) {
    /** Tek pazarlı kurulum (eski kurucu): yalnız ülke/para birimi çifti yenilenir. */
    public GamePriceProperties(String apiKey, String apiUrl, String country, String currency, Duration ttl) {
        this(apiKey, apiUrl, country, currency, ttl, "", 3);
    }
    /** Ek pazarlı kurulum (eski kurucu): gizleme süresi çarpanı varsayılandır (3). */
    public GamePriceProperties(String apiKey, String apiUrl, String country, String currency, Duration ttl, String markets) {
        this(apiKey, apiUrl, country, currency, ttl, markets, 3);
    }
    @org.springframework.boot.context.properties.bind.ConstructorBinding
    public GamePriceProperties {
        URI uri=URI.create(apiUrl);
        if (!"https".equals(uri.getScheme()) || uri.getHost()==null || uri.getUserInfo()!=null
                || uri.getQuery()!=null || uri.getFragment()!=null) throw new IllegalArgumentException("Geçersiz fiyat kaynağı.");
        new Market(country,currency);
        if(ttl==null || ttl.compareTo(Duration.ofMinutes(1))<0 || ttl.compareTo(Duration.ofHours(2))>0)
            throw new IllegalArgumentException("Fiyat TTL 1 dakika – 2 saat olmalıdır.");
        if(displayGrace<1 || displayGrace>10) throw new IllegalArgumentException("Fiyat gizleme çarpanı 1 – 10 olmalıdır.");
    }

    /**
     * Teklifin GİZLENME süresi: bir oyun kademesinin yenileme aralığının {@link #displayGrace()} katıdır. Yenileme aralığı ({@link #ttl()},
     * {@link #warmTtl()}, {@link #coldTtl()}) ne sıklıkla yeniden sorulacağını belirler; gizleme süresi ise kısa bir servis duraklamasında
     * fiyatların kaybolmamasını sağlar. Teklif, kaynağın kendi bitişi (ör. indirim sonu) daha erkense o zaman gizlenir.
     */
    public Duration displayTtl(Duration tier) { return tier.multipliedBy(displayGrace); }
    public Market market() { return new Market(country,currency); }

    /**
     * Kademeli tazelik: {@link #ttl()} sıcak kademenin süresidir (editör seçimi ve son 90 günde çıkan veya yakında çıkacak oyunlar);
     * ılık kademe (son 2 yılda çıkanlar) 6 katı, soğuk kademe (diğerleri) 24 katıdır. Bir teklif, bir sonraki planlı yenilemeye kadar
     * taze sayılır; yenileme gecikirse "eski" olarak tarihiyle gösterilir. Böylece büyük katalogda tek hızlı döngü gerekmez.
     */
    public Duration warmTtl() { return ttl.multipliedBy(6); }
    public Duration coldTtl() { return ttl.multipliedBy(24); }

    /**
     * Yenilenen pazarlar: birincil pazar her zaman ilk sırada; {@code markets} "US:USD,GB:GBP,DE:EUR" biçiminde ek pazarlar
     * verir (tekrar edenler atılır, her biri Market kurallarıyla doğrulanır). ITAD ülke başına tek para birimi döndürdüğünden
     * (ABD→USD, Birleşik Krallık→GBP, Almanya→EUR) ülke ve para birimi birlikte seçilir.
     */
    public java.util.List<Market> allMarkets() {
        java.util.LinkedHashSet<Market> all = new java.util.LinkedHashSet<>();
        all.add(market());
        if (markets != null && !markets.isBlank()) {
            for (String entry : markets.split(",")) {
                String[] parts = entry.strip().split(":");
                if (parts.length != 2) throw new IllegalArgumentException("Geçersiz fiyat pazarı: ülke:para birimi biçimi gerekir.");
                all.add(new Market(parts[0].strip(), parts[1].strip()));
            }
        }
        return java.util.List.copyOf(all);
    }
    @Override public String toString() { return "GamePriceProperties[country="+country+", currency="+currency+"]"; }
}
