package com.ltz.messaging.api.dto.response;

import java.util.UUID;

import com.ltz.messaging.application.service.AttachmentView;

/** Mesaj görseli (MSG-17). {@code url}: yetki kontrollü indirme yolu (Gateway üzerinden). */
public record AttachmentResponse(UUID id, String mimeType, int sizeBytes, int width, int height, int frameCount,
                                 String url) {

    public static AttachmentResponse from(AttachmentView view) {
        return view == null ? null : new AttachmentResponse(view.id(), view.mimeType(), view.sizeBytes(), view.width(),
                view.height(), view.frameCount(), view.url());
    }
}
