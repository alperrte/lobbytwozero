package com.ltz.messaging.application.port;

/**
 * Başka bir servisin iç ucunu çağırmak için Auth Service'ten kısa ömürlü, hedefe bağlı servis token'ı alır
 * (docs-auth/entegrasyon/README.md bölüm 3). Token ve gizli anahtar asla loglanmaz.
 */
public interface ServiceTokenPort {

    /**
     * @param audience hedef servis adı (ör. {@code social-service})
     * @return {@code Authorization: Bearer} ile gönderilecek token
     * @throws ServiceTokenUnavailableException token alınamazsa (gizli anahtar yok ya da Auth reddetti)
     */
    String tokenFor(String audience);
}
