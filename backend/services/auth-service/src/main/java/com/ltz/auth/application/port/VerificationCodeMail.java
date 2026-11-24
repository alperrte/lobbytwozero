package com.ltz.auth.application.port;

import java.time.Duration;
import java.util.UUID;

/**
 * Mail Service'e iletilecek doğrulama içeriği. Yalnız bellekte yaşar, hiçbir tabloya yazılmaz.
 *
 * @param idempotencyKey kod kaydının kimliği; Mail Service'te mükerrer gönderimi engeller (MAIL-14)
 * @param requestLocale isteğin arayüz dili ({@code tr}/{@code en}) ya da {@code null}; yalnız kayıtlı tercih yoksa kullanılır
 */
public record VerificationCodeMail(UUID idempotencyKey, UUID accountId, String recipientEmail,
                                   String code, Duration validity, String requestLocale) {

    public VerificationCodeMail(UUID idempotencyKey, UUID accountId, String recipientEmail, String code,
                                Duration validity) {
        this(idempotencyKey, accountId, recipientEmail, code, validity, null);
    }

    public VerificationCodeMail withRequestLocale(String locale) {
        return new VerificationCodeMail(idempotencyKey, accountId, recipientEmail, code, validity, locale);
    }

    /** Kod ve alıcı adresi log/hata mesajlarına sızmasın diye maskelenir. */
    @Override
    public String toString() {
        return "VerificationCodeMail[idempotencyKey=" + idempotencyKey + ", accountId=" + accountId
                + ", recipientEmail=***, code=***, validity=" + validity + "]";
    }
}
