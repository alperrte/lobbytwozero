package com.ltz.messaging.application.service;

import java.time.Instant;
import java.util.UUID;

import com.ltz.messaging.domain.enums.ConversationType;
import com.ltz.messaging.domain.enums.MemberRole;

/**
 * Sohbet listesi satırı (MSG-08). {@code peerUserId} yalnız birebir sohbette karşı taraftır. {@code lastMessage}
 * kullanıcının göremediği (katılmadan önceki) mesajsa {@code null}'dır.
 */
public record ConversationSummaryView(UUID id, ConversationType type, String title, UUID peerUserId, long memberCount,
                                      MemberRole myRole, Instant lastActivityAt, MessageView lastMessage,
                                      long unreadCount) {
}
