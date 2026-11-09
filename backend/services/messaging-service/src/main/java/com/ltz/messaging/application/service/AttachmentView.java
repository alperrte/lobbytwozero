package com.ltz.messaging.application.service;

import java.util.UUID;

import com.ltz.messaging.infrastructure.persistence.repository.AttachmentMeta;

/**
 * Mesaj görselinin bilgileri (MSG-17). İçerik ayrı, yetki kontrollü uçtan ({@code url}, Gateway yolu) indirilir;
 * {@code frameCount} GIF'te kare sayısı, diğerlerinde 1'dir. Geçmiş, gönderim yanıtı ve akış olayı aynı alanları taşır.
 */
public record AttachmentView(UUID id, String mimeType, int sizeBytes, int width, int height, int frameCount,
                             String url) {

    /** {@code MessagingApiPaths.ATTACHMENT} ile aynı yol; uygulama katmanı API katmanını içe aktarmaz. */
    static final String URL_TEMPLATE = "/api/v1/messaging/conversations/%s/attachments/%s";

    public static AttachmentView of(AttachmentMeta meta) {
        return of(meta.id(), meta.conversationId(), meta.mimeType(), meta.sizeBytes(), meta.width(), meta.height(),
                meta.frameCount());
    }

    static AttachmentView of(UUID id, UUID conversationId, String mimeType, int sizeBytes, int width, int height,
                             int frameCount) {
        return new AttachmentView(id, mimeType, sizeBytes, width, height, frameCount,
                URL_TEMPLATE.formatted(conversationId, id));
    }
}
