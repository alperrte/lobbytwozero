package com.ltz.game.infrastructure.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Game → Review LTZ puan projeksiyonu. Adresler ortak ENV değerlerinden gelir; istemci gizli anahtarı yalnız
 * {@code AUTH_GAME_SERVICE_CLIENT_SECRET} ortam değişkeninden gelir, repoya yazılmaz. Gizli anahtar boşsa istemci
 * kapalıdır: Review'a istek atılmaz ve puan filtre/sıralaması "hazır değil" kalır (fail-closed).
 *
 * @param reviewServiceUrl Review Service iç adresi
 * @param authServiceUrl   Auth Service adresi (servis token'ı için)
 * @param tokenPath        Auth servis token yolu
 * @param clientId         Auth'taki istemci kaydının adı
 * @param clientSecret     istemci gizli anahtarı; boş olabilir (istemci kapalı)
 * @param audience         token hedefi (Review Service)
 * @param connectTimeout   Auth ve Review çağrıları için bağlantı zaman aşımı
 * @param readTimeout      Auth ve Review çağrıları için okuma zaman aşımı
 * @param pageSize         dışa aktarma sayfa büyüklüğü (1–500)
 * @param sync             periyodik tarama ayarları
 */
@ConfigurationProperties("ltz.game.rating")
public record GameRatingProperties(
        @DefaultValue("http://review-service:8080") String reviewServiceUrl,
        String authServiceUrl,
        @DefaultValue("/internal/v1/service-tokens") String tokenPath,
        @DefaultValue("game-service") String clientId,
        String clientSecret,
        @DefaultValue("review-service") String audience,
        @DefaultValue("1s") Duration connectTimeout,
        @DefaultValue("2s") Duration readTimeout,
        @DefaultValue("500") int pageSize,
        @DefaultValue Sync sync) {

    public GameRatingProperties {
        if (pageSize < 1 || pageSize > 500) {
            throw new IllegalArgumentException("ltz.game.rating.page-size 1–500 olmalıdır.");
        }
    }

    /**
     * @param enabled      periyodik taramayı zamanlayıcıya bağlar
     * @param interval     iki tarama arası bekleme; puanın tazelik sınırı bu süredir
     * @param initialDelay açılıştan ilk taramaya bekleme
     * @param leaseTtl     tarama kiralama süresi (her sayfada yenilenir); çok örnekli dağıtımda tek tarayıcıyı korur
     */
    public record Sync(
            @DefaultValue("true") boolean enabled,
            @DefaultValue("PT5M") Duration interval,
            @DefaultValue("PT30S") Duration initialDelay,
            @DefaultValue("PT2M") Duration leaseTtl) { }

    @Override
    public String toString() {
        // Gizli anahtar kazara loglanmasın.
        return "GameRatingProperties[reviewServiceUrl=" + reviewServiceUrl + ", clientId=" + clientId + "]";
    }
}
