package com.ltz.mail.application.port;

import java.util.UUID;

/**
 * SMTP'ye verilecek hazır e-posta. İçerik (kod/bağlantı) taşıdığı için {@code toString()} hiçbir şey göstermez;
 * kayıt loga ya da hata mesajına girmemelidir.
 */
public record OutgoingMail(UUID messageId, String to, String subject, String text, String html) {

    @Override
    public String toString() {
        return "OutgoingMail[messageId=" + messageId + ", to=***, subject=***, text=***, html=***]";
    }
}
