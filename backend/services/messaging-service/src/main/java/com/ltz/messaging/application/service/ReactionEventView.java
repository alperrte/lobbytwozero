package com.ltz.messaging.application.service;

import java.util.List;
import java.util.UUID;

/** {@code reaction} olayının yükü (MSG-23): güncel özet ve alıcının kendi tepkisi ({@code mine}). */
public record ReactionEventView(UUID conversationId, UUID messageId, List<ReactionCount> summary, String mine) {

    public ReactionEventView {
        summary = List.copyOf(summary);
    }
}
