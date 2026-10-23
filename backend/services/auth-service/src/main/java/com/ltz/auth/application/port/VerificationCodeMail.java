package com.ltz.auth.application.port;

import java.time.Duration;
import java.util.UUID;

/**
 * Mail Service'e iletilecek doğrulama içeriği. Yalnız bellekte yaşar, hiçbir tabloya yazılmaz.
 *
 * @param idempotencyKey kod kaydının kimliği; Mail Service'te mükerrer gönderimi engeller (MAIL-14)
 */
public record VerificationCodeMail(UUID idempotencyKey, UUID accountId, String recipientEmail,
                                   String code, Duration validity) {

    /** Kod ve alıcı adresi log/hata mesajlarına sızmasın diye maskelenir. */
    @Override
    public String toString() {
        return "VerificationCodeMail[idempotencyKey=" + idempotencyKey + ", accountId=" + accountId
                + ", recipientEmail=***, code=***, validity=" + validity + "]";
    }
}
