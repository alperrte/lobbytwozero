package com.ltz.mail.application.service;

import com.ltz.mail.domain.enums.NoticeKind;

/**
 * Genel bilgilendirme içeriği (platform kapısı). Hepsi DÜZ METİNDİR; render sırasında HTML-kaçışlanır.
 *
 * @param kind        görünüm rengi: INFO, SUCCESS, WARNING, DANGER
 * @param title       başlık (aynı zamanda konu satırı)
 * @param message     metin; boş satırla ayrılan bölümler ayrı paragraf olur
 * @param actionLabel isteğe bağlı düğme yazısı ({@code actionLink} ile birlikte)
 * @param actionLink  isteğe bağlı düğme adresi (https, izinli host)
 */
public record NoticeContent(NoticeKind kind, String title, String message, String actionLabel, String actionLink) {

    @Override
    public String toString() {
        return "NoticeContent[kind=" + kind + ", content=***]";
    }
}
