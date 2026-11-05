package com.ltz.game.infrastructure.config;

import com.ltz.game.application.port.GamePriceProvider.Market;
import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("ltz.game.pricing")
public record GamePriceProperties(@DefaultValue("") String apiKey,
        @DefaultValue("https://api.isthereanydeal.com") String apiUrl,
        @DefaultValue("TR") String country, @DefaultValue("USD") String currency,
        @DefaultValue("15m") Duration ttl) {
    public GamePriceProperties {
        URI uri=URI.create(apiUrl);
        if (!"https".equals(uri.getScheme()) || uri.getHost()==null || uri.getUserInfo()!=null
                || uri.getQuery()!=null || uri.getFragment()!=null) throw new IllegalArgumentException("Geçersiz fiyat kaynağı.");
        new Market(country,currency);
        if(ttl==null || ttl.compareTo(Duration.ofMinutes(1))<0 || ttl.compareTo(Duration.ofHours(1))>0)
            throw new IllegalArgumentException("Fiyat TTL 1–60 dakika olmalıdır.");
    }
    public Market market() { return new Market(country,currency); }
    @Override public String toString() { return "GamePriceProperties[country="+country+", currency="+currency+"]"; }
}
