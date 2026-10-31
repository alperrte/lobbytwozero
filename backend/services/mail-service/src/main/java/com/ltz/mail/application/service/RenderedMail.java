package com.ltz.mail.application.service;

/** Gönderime hazır e-posta içeriği (kod/bağlantı içerir; loga yazılmaz). */
public record RenderedMail(String subject, String html, String text) {

    @Override
    public String toString() {
        return "RenderedMail[subject=***, html=***, text=***]";
    }
}
