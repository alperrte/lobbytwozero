package com.ltz.game.infrastructure.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * IGDB detay kaynağı (GAME-17/23). Kimlik bilgileri yalnız ortam değişkeninden gelir, repoya yazılmaz; ikisi de
 * doluysa kaynak etkinleşir, aksi halde hiç istek atılmaz (fail-closed).
 *
 * @param clientId     Twitch uygulama kimliği
 * @param clientSecret Twitch uygulama gizli anahtarı
 * @param apiUrl       IGDB v4 taban adresi
 * @param tokenUrl     Twitch OAuth token adresi
 * @param connectTimeout bağlantı zaman aşımı
 * @param readTimeout  okuma zaman aşımı
 * @param minInterval  iki IGDB isteği arası en az süre (sınır 4 istek/sn)
 */
@ConfigurationProperties("ltz.game.igdb")
public record IgdbProperties(
        String clientId,
        String clientSecret,
        @DefaultValue("https://api.igdb.com/v4") String apiUrl,
        @DefaultValue("https://id.twitch.tv/oauth2/token") String tokenUrl,
        @DefaultValue("2s") Duration connectTimeout,
        @DefaultValue("5s") Duration readTimeout,
        @DefaultValue("300ms") Duration minInterval) {

    public IgdbProperties {
        if (apiUrl == null || tokenUrl == null || !apiUrl.startsWith("https://") || !tokenUrl.startsWith("https://")) {
            throw new IllegalArgumentException("IGDB ve Twitch adresleri https:// olmalıdır.");
        }
        if (minInterval == null || minInterval.isNegative() || minInterval.compareTo(Duration.ofSeconds(10)) > 0) {
            throw new IllegalArgumentException("ltz.game.igdb.min-interval 0–10 sn olmalıdır.");
        }
    }

    @Override
    public String toString() {
        // Gizli anahtar kazara loglanmasın.
        return "IgdbProperties[apiUrl=" + apiUrl + ", tokenUrl=" + tokenUrl + "]";
    }
}
