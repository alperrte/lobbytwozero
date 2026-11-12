package com.ltz.game.infrastructure.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * ITAD istek hızı ayarları ({@code ltz.game.pricing.rate.*}). Varsayılanlar canlı (doğrulanmış hesap) içindir; geliştirme makinesi ayarları
 * {@code application.properties}'te daha düşüktür, çünkü DEV anahtarı ekip tarafından paylaşılır ve ölçülen gerçek eşik 5 dakikada ≈160-220 istektir.
 *
 * <p>{@code adaptive}: kendi trafiğimiz etkin limitin yarısına ulaşmışken 429 alınırsa etkin limit, o ana kadar atılan istek sayısının %70'ine (en az {@code floor}) indirilir
 * (küçük bir gözlemde gelen 429 limiti düşürmez); 429 olmadan geçen her {@code increaseInterval}'de {@code limit/10} artar ve {@code limit}'i aşmaz.
 */
@ConfigurationProperties("ltz.game.pricing.rate")
public record ItadRateProperties(@DefaultValue("850") int limit, @DefaultValue("150") int reserve, @DefaultValue("5m") Duration window,
        @DefaultValue("20s") Duration maxWait, @DefaultValue("5s") Duration interactiveMaxWait, @DefaultValue("true") boolean adaptive,
        @DefaultValue("30") int floor, @DefaultValue("2m") Duration increaseInterval) {
    @org.springframework.boot.context.properties.bind.ConstructorBinding
    public ItadRateProperties {
        if (limit < 2 || reserve < 0 || reserve >= limit) throw new IllegalArgumentException("ITAD hız limiti en az 2 olmalı ve ayrılmış pay limitten küçük olmalıdır.");
        if (floor < 2 || floor > limit) throw new IllegalArgumentException("ITAD etkin limit tabanı 2 ile limit arasında olmalıdır.");
        if (window == null || window.isZero() || window.isNegative()) throw new IllegalArgumentException("ITAD hız penceresi pozitif olmalıdır.");
        if (maxWait == null || maxWait.isNegative() || interactiveMaxWait == null || interactiveMaxWait.isNegative()) throw new IllegalArgumentException("ITAD bekleme süreleri negatif olamaz.");
        if (increaseInterval == null || increaseInterval.isZero() || increaseInterval.isNegative()) throw new IllegalArgumentException("ITAD artış aralığı pozitif olmalıdır.");
    }

    /** Canlı varsayılanları. */
    public static ItadRateProperties defaults() {
        return new ItadRateProperties(850, 150, Duration.ofMinutes(5), Duration.ofSeconds(20), Duration.ofSeconds(5), true, 30, Duration.ofMinutes(2));
    }
}
