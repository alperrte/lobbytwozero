package com.ltz.mail.application.port;

import java.util.UUID;

/**
 * Platform kapısında alıcı adresinin çözümü: servisler yalnız {@code accountId} verir, adres Auth'ta yaşar ve gönderim
 * anında çözülür (kuyrukta saklanmaz). Hesap yok / geçici hata {@link MailDeliveryException} ile bildirilir
 * (kalıcı: yeniden deneme anlamsız; geçici: kuyrukta bekler).
 */
public interface RecipientResolverPort {

    /** Gizli anahtar yoksa {@code false}: platform kapısı istek kabul etmez. */
    boolean isConfigured();

    /** @throws MailDeliveryException hesap yoksa (kalıcı) ya da Auth erişilemiyorsa (geçici) */
    AccountContact resolve(UUID accountId);

    /**
     * @param email         adres; adresi olmayan (yalnız Steam) ya da anonimleştirilmiş hesapta {@code null}
     * @param emailVerified adres doğrulanmış mı
     * @param status        hesap durumu (ACTIVE, SUSPENDED, BANNED, ...)
     */
    record AccountContact(String email, boolean emailVerified, String status) {

        @Override
        public String toString() {
            return "AccountContact[status=" + status + ", emailVerified=" + emailVerified + ", email=***]";
        }
    }
}
