package com.ltz.mail.application.service;

import java.time.Instant;

/**
 * E-postanın hassas/değişken içeriği. Yalnız bellekte ve şifreli blob olarak yaşar; {@code toString()} değerleri gizler.
 *
 * @param code            6 haneli doğrulama kodu (yalnız {@code EMAIL_VERIFICATION})
 * @param link            sıfırlama/onay bağlantısı (yalnız bağlantı türleri)
 * @param validitySeconds kod/bağlantı geçerlilik süresi; güvenlik bildirimlerinde {@code null}
 * @param occurredAt      olayın zamanı (güvenlik bildirimleri)
 */
public record MailPayload(String code, String link, Integer validitySeconds, Instant occurredAt) {

    @Override
    public String toString() {
        return "MailPayload[code=***, link=***, validitySeconds=" + validitySeconds + ", occurredAt=" + occurredAt + "]";
    }
}
