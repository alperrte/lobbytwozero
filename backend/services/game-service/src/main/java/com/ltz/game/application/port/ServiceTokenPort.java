package com.ltz.game.application.port;

/**
 * Başka bir servisin iç ucunu çağırmak için Auth Service'ten kısa ömürlü servis token'ı alır. Her hedef için ayrı token
 * verilir ve yalnız o hedefte geçer. Token ve gizli anahtar asla loglanmaz; kullanıcı token'ına geri düşülmez.
 */
public interface ServiceTokenPort {

    /** İstemci gizli anahtarı tanımlı mı; değilse token alınamaz ve giden çağrılar hiç denenmez (fail-closed). */
    boolean isConfigured();

    /**
     * @param audience hedef servis adı (ör. {@code review-service})
     * @return {@code Authorization: Bearer} ile gönderilecek token
     * @throws com.ltz.game.domain.exception.ServiceTokenUnavailableException token alınamazsa
     */
    String tokenFor(String audience);

    /** Hedef reddettiyse bellekteki token'ı atar; sonraki çağrı yeni token alır (aynı çağrıda yeniden deneme yok). */
    void invalidate(String audience);
}
