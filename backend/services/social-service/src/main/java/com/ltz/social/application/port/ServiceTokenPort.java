package com.ltz.social.application.port;

/**
 * Başka bir servisin iç ucunu çağırmak için Auth Service'ten kısa ömürlü servis token'ı alır
 * (docs-auth/entegrasyon/README.md bölüm 3). Her hedef için ayrı token verilir; token yalnız o hedefte geçer.
 * Token ve gizli anahtar asla loglanmaz.
 */
public interface ServiceTokenPort {

    /** İstemci gizli anahtarı tanımlı mı; değilse token alınamaz ve giden çağrılar hiç denenmez (fail-closed). */
    boolean isConfigured();

    /**
     * @param audience hedef servis adı (ör. {@code community-service})
     * @return {@code Authorization: Bearer} ile gönderilecek token
     * @throws com.ltz.social.domain.exception.ServiceTokenUnavailableException token alınamazsa
     */
    String tokenFor(String audience);
}
