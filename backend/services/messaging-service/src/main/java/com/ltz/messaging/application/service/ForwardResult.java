package com.ltz.messaging.application.service;

import java.util.List;

/** İletme sonucu (MSG-20): hedef sırasıyla oluşan mesajlar; {@code created} yeniden denemede {@code false}. */
public record ForwardResult(List<MessageView> messages, boolean created) {

    public ForwardResult {
        messages = List.copyOf(messages);
    }
}
