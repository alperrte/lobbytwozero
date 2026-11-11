package com.ltz.messaging.domain.enums;

/**
 * Mesaj türü. {@code IMAGE}: sohbete yüklenmiş görsel (PNG, JPEG ya da GIF dosyası, MSG-17). {@code GIF}: GIF
 * platformundan seçilmiş GIF (MSG-22); dosyası saklanmaz, sağlayıcı adresiyle gösterilir. {@code VOICE}: sesli mesaj
 * (MSG-21); yeniden paketlenmiş Opus/WebM dosyası, açıklama taşımaz.
 */
public enum MessageType {
    TEXT,
    IMAGE,
    GIF,
    VOICE;

    /** Mesaja bağlı yüklenmiş bir dosya ({@code message_attachments}) var mı? */
    public boolean hasAttachment() {
        return this == IMAGE || this == VOICE;
    }
}
