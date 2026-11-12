package com.ltz.mail.application.service;

import java.time.Instant;

/**
 * E-postanın hassas/değişken içeriği. Yalnız bellekte ve şifreli blob olarak yaşar; {@code toString()} değerleri gizler.
 *
 * @param code            6 haneli doğrulama kodu (yalnız {@code EMAIL_VERIFICATION})
 * @param link            sıfırlama/onay bağlantısı (yalnız bağlantı türleri)
 * @param validitySeconds kod/bağlantı geçerlilik süresi; güvenlik bildirimlerinde {@code null}
 * @param occurredAt      olayın zamanı (güvenlik bildirimleri)
 * @param notice          genel bilgilendirme içeriği (yalnız {@code NOTICE}); diğer türlerde {@code null}
 */
public record MailPayload(String code, String link, Integer validitySeconds, Instant occurredAt, NoticeContent notice) {

    /** Bilgilendirme içeriği olmayan türler için. */
    public MailPayload(String code, String link, Integer validitySeconds, Instant occurredAt) {
        this(code, link, validitySeconds, occurredAt, null);
    }

    @Override
    public String toString() {
        return "MailPayload[code=***, link=***, validitySeconds=" + validitySeconds + ", occurredAt=" + occurredAt
                + ", notice=" + (notice == null ? "none" : "***") + "]";
    }
}
