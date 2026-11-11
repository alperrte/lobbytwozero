package com.ltz.messaging.api.dto.response;

import java.util.List;
import java.util.UUID;

import com.ltz.messaging.application.service.AttachmentView;

/**
 * Mesaj eki: görsel (MSG-17) ya da sesli mesaj (MSG-21). {@code url}: yetki kontrollü indirme yolu (Gateway üzerinden).
 * Görselde {@code width}, {@code height}, {@code frameCount}; seste {@code durationMs} ve isteğe bağlı {@code waveform}
 * (64 değer, 0–100) doludur, diğer alanlar {@code null}'dır. Seste {@code mimeType} her zaman {@code audio/webm}.
 */
public record AttachmentResponse(UUID id, String mimeType, int sizeBytes, Integer width, Integer height,
                                 Integer frameCount, Integer durationMs, List<Integer> waveform, String url) {

    public static AttachmentResponse from(AttachmentView view) {
        return view == null ? null : new AttachmentResponse(view.id(), view.mimeType(), view.sizeBytes(), view.width(),
                view.height(), view.frameCount(), view.durationMs(), view.waveform(), view.url());
    }
}
