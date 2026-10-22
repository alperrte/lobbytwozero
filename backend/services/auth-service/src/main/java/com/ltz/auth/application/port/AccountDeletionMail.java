package com.ltz.auth.application.port;

import java.time.Duration;
import java.util.UUID;

/**
 * Hesap silme onay e-postası (MAIL-03 güvenlik e-postaları). Bağlantı token'ı taşır; yalnız bellekte yaşar,
 * hiçbir tabloya veya log'a yazılmaz.
 *
 * @param idempotencyKey token kaydının kimliği; mükerrer gönderimi engeller (MAIL-14)
 */
public record AccountDeletionMail(UUID idempotencyKey, UUID accountId, String recipientEmail, String confirmationLink,
                                  Duration validity) {

    @Override
    public String toString() {
        return "AccountDeletionMail[idempotencyKey=" + idempotencyKey + ", accountId=" + accountId
                + ", recipientEmail=***, confirmationLink=***, validity=" + validity + "]";
    }
}
