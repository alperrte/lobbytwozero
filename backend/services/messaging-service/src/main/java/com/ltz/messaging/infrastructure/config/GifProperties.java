package com.ltz.messaging.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * GIF platformu (MSG-22; GIPHY). Anahtar yalnız ortam değişkeninden ({@code GIPHY_API_KEY}) gelir, repoya yazılmaz; boşsa
 * GIF arama ve GIF gönderme fail-closed {@code 503 GIF_PROVIDER_UNAVAILABLE} döner, diğer mesajlaşma etkilenmez.
 *
 * @param apiUrl            platform API kökü
 * @param apiKey            API anahtarı; boş olabilir (kapalı)
 * @param rating            izin verilen en yüksek içerik derecesi ({@code g}, {@code pg}, {@code pg-13}, {@code r})
 * @param defaultLimit      sayfa boyutu varsayılanı
 * @param maxLimit          en büyük sayfa boyutu
 * @param maxOffset         en büyük başlangıç konumu
 * @param maxQueryLength    arama metni en çok karakter
 * @param searchesPerMinute kullanıcı başına son 60 sn'de en çok arama (arama + öne çıkanlar)
 */
@Validated
@ConfigurationProperties("ltz.messaging.gifs")
public record GifProperties(
        @NotBlank String apiUrl,
        String apiKey,
        @NotBlank @Pattern(regexp = "g|pg|pg-13|r") String rating,
        @Min(1) int defaultLimit,
        @Min(1) @Max(50) int maxLimit,
        @Min(0) @Max(4999) int maxOffset,
        @Min(1) @Max(100) int maxQueryLength,
        @Min(1) int searchesPerMinute) {

    public boolean hasApiKey() {
        return apiKey != null && !apiKey.isBlank();
    }

    @Override
    public String toString() {
        // Anahtar kazara loglanmasın.
        return "GifProperties[apiUrl=%s, rating=%s, defaultLimit=%d, maxLimit=%d]"
                .formatted(apiUrl, rating, defaultLimit, maxLimit);
    }
}
