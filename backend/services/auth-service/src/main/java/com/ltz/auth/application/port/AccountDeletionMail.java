package com.ltz.auth.application.port;

import java.time.Duration;
import java.util.UUID;

/**
 * Hesap silme onay e-postası (MAIL-03 güvenlik e-postaları). Bağlantı token'ı taşır; yalnız bellekte yaşar,
 * hiçbir tabloya veya log'a yazılmaz.
 *
 * @param idempotencyKey token kaydının kimliği; mükerrer gönderimi engeller (MAIL-14)
 * @param requestLocale isteğin arayüz dili ({@code tr}/{@code en}) ya da {@code null}; yalnız kayıtlı tercih yoksa kullanılır
 */
public record AccountDeletionMail(UUID idempotencyKey, UUID accountId, String recipientEmail, String confirmationLink,
                                  Duration validity, String requestLocale) {

    public AccountDeletionMail(UUID idempotencyKey, UUID accountId, String recipientEmail, String confirmationLink,
                               Duration validity) {
        this(idempotencyKey, accountId, recipientEmail, confirmationLink, validity, null);
    }

    public AccountDeletionMail withRequestLocale(String locale) {
        return new AccountDeletionMail(idempotencyKey, accountId, recipientEmail, confirmationLink, validity, locale);
    }

    @Override
    public String toString() {
        return "AccountDeletionMail[idempotencyKey=" + idempotencyKey + ", accountId=" + accountId
                + ", recipientEmail=***, confirmationLink=***, validity=" + validity + "]";
    }
}
