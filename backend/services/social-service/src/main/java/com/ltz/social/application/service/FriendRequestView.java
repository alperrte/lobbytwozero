package com.ltz.social.application.service;

import java.time.Instant;
import java.util.UUID;

import com.ltz.social.domain.enums.FriendRequestStatus;

/** Arkadaşlık isteğinin application katmanı çıktısı (entity API'ye sızmaz; sürüm gibi iç alanlar yoktur). */
public record FriendRequestView(UUID id, UUID senderId, UUID receiverId, FriendRequestStatus status,
                                Instant createdAt, Instant respondedAt) {
}
