package com.ltz.game.infrastructure.security;

import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Uç nokta bazlı istek sınırı ayarları (application.properties; ENV anahtarı eklenmez).
 *
 * @param batch          herkese açık toplu kart ucu için kova ayarı
 * @param maxKeys        aynı anda izlenen en fazla istemci anahtarı (bellek sınırı); dolunca yeni anahtarlar ortak taşma kovasını paylaşır
 * @param trustedProxies {@code X-Forwarded-For} başlığına güvenilecek doğrudan bağlantı adresleri (CIDR). Varsayılan BOŞTUR:
 *                       başlık yok sayılır ve anahtar doğrudan bağlantı adresidir. Gateway istemci IP'sini güvenilir biçimde
 *                       iletecek şekilde yapılandırılıp doğrulanmadan doldurulmamalıdır (sahte başlıkla sınır atlatılır).
 */
@ConfigurationProperties("ltz.rate-limit")
public record RateLimitProperties(@DefaultValue Batch batch, @DefaultValue("10000") int maxKeys, Set<String> trustedProxies) {
    /**
     * @param capacity        ani yük (kova kapasitesi)
     * @param refillPerSecond sürekli izin verilen saniyedeki istek sayısı
     */
    public record Batch(@DefaultValue("100") int capacity, @DefaultValue("20") double refillPerSecond) {
        public Batch {
            if (capacity < 1 || capacity > 100_000 || Double.isNaN(refillPerSecond) || refillPerSecond <= 0 || refillPerSecond > 100_000)
                throw new IllegalStateException("Geçersiz istek sınırı yapılandırması.");
        }
    }

    public RateLimitProperties {
        if (maxKeys < 100 || maxKeys > 1_000_000) throw new IllegalStateException("Geçersiz istek sınırı yapılandırması.");
        trustedProxies = trustedProxies == null ? Set.of() : trustedProxies.stream().map(String::strip).filter(value -> !value.isEmpty())
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }
}
