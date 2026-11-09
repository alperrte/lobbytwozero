package com.ltz.messaging.application.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Bir mesajın tepki listesi (MSG-23): kim, hangi emoji, ne zaman; yeniden eskiye. */
public record MessageReactionsView(UUID messageId, List<Entry> reactions) {

    public MessageReactionsView {
        reactions = List.copyOf(reactions);
    }

    /** Bir kullanıcının tepkisi. */
    public record Entry(UUID userId, String emoji, Instant reactedAt) {
    }
}
