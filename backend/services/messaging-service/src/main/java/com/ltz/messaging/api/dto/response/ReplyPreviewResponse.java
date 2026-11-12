package com.ltz.messaging.api.dto.response;

import java.util.UUID;

import com.ltz.messaging.application.service.ReplyPreview;
import com.ltz.messaging.domain.enums.MessageType;

/**
 * Yanıtın alıntı önizlemesi (MSG-19). {@code deleted}: hedef herkesten silindi (yalnız kimlik, sıra, gönderen ve tür
 * döner). {@code unavailable}: isteyen hedefi göremiyor (yalnız kimlik döner).
 */
public record ReplyPreviewResponse(UUID messageId, Long seq, UUID senderId, MessageType type, String snippet,
                                   boolean deleted, boolean unavailable) {

    public static ReplyPreviewResponse from(ReplyPreview preview) {
        return preview == null ? null : new ReplyPreviewResponse(preview.messageId(), preview.seq(),
                preview.senderId(), preview.type(), preview.snippet(), preview.deleted(), preview.unavailable());
    }
}
