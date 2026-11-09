package com.ltz.messaging.domain.enums;

/**
 * Mesaj türü. {@code IMAGE}: sohbete yüklenmiş görsel (PNG, JPEG ya da GIF dosyası, MSG-17). {@code GIF}: GIF
 * platformundan seçilmiş GIF (MSG-22); dosyası saklanmaz, sağlayıcı adresiyle gösterilir.
 */
public enum MessageType {
    TEXT,
    IMAGE,
    GIF
}
