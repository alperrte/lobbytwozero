package com.ltz.auth.application.port;

import java.time.Duration;
import java.util.UUID;

/**
 * Parola sıfırlama e-postası içeriği (MAIL-02). Bağlantı token'ı taşır; yalnız bellekte yaşar, hiçbir tabloya
 * veya log'a yazılmaz.
 *
 * @param idempotencyKey token kaydının kimliği; mükerrer gönderimi engeller (MAIL-14)
 */
public record PasswordResetMail(UUID idempotencyKey, UUID accountId, String recipientEmail, String resetLink,
                                Duration validity) {

    @Override
    public String toString() {
        return "PasswordResetMail[idempotencyKey=" + idempotencyKey + ", accountId=" + accountId
                + ", recipientEmail=***, resetLink=***, validity=" + validity + "]";
    }
}
