package com.ltz.messaging.application.service;

import java.util.UUID;

/** Okuma işaretçisinin güncel hali ve kalan okunmamış mesaj sayısı (MSG-13). */
public record ReadStateView(UUID conversationId, long lastReadSeq, long unreadCount) {
}
