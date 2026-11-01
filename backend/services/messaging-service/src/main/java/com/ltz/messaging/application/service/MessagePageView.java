package com.ltz.messaging.application.service;

import java.util.List;

/** Mesaj sayfası; {@code hasMore} aynı yönde daha fazla mesaj olduğunu söyler. */
public record MessagePageView(List<MessageView> items, boolean hasMore) {
}
