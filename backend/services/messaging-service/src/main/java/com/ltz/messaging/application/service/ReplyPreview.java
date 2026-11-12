package com.ltz.messaging.application.service;

import java.util.UUID;

import com.ltz.messaging.domain.entity.Message;
import com.ltz.messaging.domain.enums.MessageType;

/**
 * Yanıtın alıntı önizlemesi (MSG-19), okuyana göre: hedefi görebiliyorsa gönderen, tür ve kısa metin; hedef herkesten
 * silinmişse yalnız "silindi"; hedefi göremiyorsa (katılmadan önceki ya da benden silinmiş mesaj) yalnız kimlik ve
 * "görüntülenemiyor". Göremeyen okuyana içerik ve gönderen sızmaz.
 */
public record ReplyPreview(UUID messageId, Long seq, UUID senderId, MessageType type, String snippet, boolean deleted,
                           boolean unavailable) {

    /** Önizleme metninin azami uzunluğu (UTF-16 birimi, JavaScript {@code length} ile aynı). */
    static final int SNIPPET_LENGTH = 100;

    public static ReplyPreview visible(Message target) {
        return new ReplyPreview(target.getId(), target.getSeq(), target.getSenderId(), target.getType(),
                snippet(target.getBody()), false, false);
    }

    public static ReplyPreview deleted(Message target) {
        return new ReplyPreview(target.getId(), target.getSeq(), target.getSenderId(), target.getType(), null, true,
                false);
    }

    public static ReplyPreview unavailable(UUID messageId) {
        return new ReplyPreview(messageId, null, null, null, null, false, true);
    }

    /** İlk {@value #SNIPPET_LENGTH} UTF-16 birimi; vekil çifti (emoji) ortadan bölünmez. */
    public static String snippet(String body) {
        if (body == null || body.length() <= SNIPPET_LENGTH) {
            return body;
        }
        int end = Character.isHighSurrogate(body.charAt(SNIPPET_LENGTH - 1)) ? SNIPPET_LENGTH - 1 : SNIPPET_LENGTH;
        return body.substring(0, end);
    }
}
