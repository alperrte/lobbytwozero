package com.ltz.messaging.api.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.ltz.messaging.application.service.MessageReactionsView;

/** Mesajın tepki listesi (MSG-23): kim, hangi emoji, ne zaman; yeniden eskiye. Silinmiş mesajda boştur. */
public record MessageReactionsResponse(UUID messageId, List<Entry> reactions) {

    /** Bir kullanıcının tepkisi. */
    public record Entry(UUID userId, String emoji, Instant reactedAt) {
    }

    public static MessageReactionsResponse from(MessageReactionsView view) {
        return new MessageReactionsResponse(view.messageId(), view.reactions().stream()
                .map(entry -> new Entry(entry.userId(), entry.emoji(), entry.reactedAt()))
                .toList());
    }
}
