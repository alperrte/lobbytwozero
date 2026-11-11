package com.ltz.messaging.application.service;

import java.util.List;
import java.util.UUID;

import com.ltz.messaging.domain.entity.MessageAttachment;
import com.ltz.messaging.infrastructure.persistence.repository.AttachmentMeta;

/**
 * Mesaj ekinin bilgileri: görsel (MSG-17) ya da sesli mesaj (MSG-21). İçerik ayrı, yetki kontrollü uçtan ({@code url},
 * Gateway yolu) indirilir. Görselde {@code width}, {@code height}, {@code frameCount} (GIF'te kare sayısı, diğerlerinde
 * 1) dolu; seste {@code durationMs} ve isteğe bağlı {@code waveform} (64 değer, 0–100) dolu, diğerleri {@code null}.
 * Geçmiş, gönderim yanıtı ve akış olayı aynı alanları taşır.
 */
public record AttachmentView(UUID id, String mimeType, int sizeBytes, Integer width, Integer height,
                             Integer frameCount, Integer durationMs, List<Integer> waveform, String url) {

    /** {@code MessagingApiPaths.ATTACHMENT} ile aynı yol; uygulama katmanı API katmanını içe aktarmaz. */
    static final String URL_TEMPLATE = "/api/v1/messaging/conversations/%s/attachments/%s";

    public static AttachmentView of(AttachmentMeta meta) {
        return new AttachmentView(meta.id(), meta.mimeType(), meta.sizeBytes(), meta.width(), meta.height(),
                meta.frameCount(), meta.durationMs(), Waveform.toList(meta.waveform()),
                URL_TEMPLATE.formatted(meta.conversationId(), meta.id()));
    }

    static AttachmentView of(MessageAttachment saved) {
        return new AttachmentView(saved.getId(), saved.getMimeType(), saved.getSizeBytes(), saved.getWidth(),
                saved.getHeight(), saved.getFrameCount(), saved.getDurationMs(), Waveform.toList(saved.getWaveform()),
                URL_TEMPLATE.formatted(saved.getConversationId(), saved.getId()));
    }
}
