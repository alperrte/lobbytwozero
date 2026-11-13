package com.ltz.lfp.application.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.ltz.lfp.domain.enums.ListingStatus;
import com.ltz.lfp.domain.enums.Platform;

/**
 * İlanın dışarıya açılan görünümü; entity API'ye dönmez. {@code status} okuma anındaki durumdur (süresi geçmiş açık
 * ilan {@code EXPIRED}). {@code version} güncellemede eşzamanlı değişikliği yakalamak için istemciye verilir.
 */
public record ListingView(UUID id, UUID ownerId, UUID gameId, String gameTitle, Platform platform, String skillLevel,
                          List<String> roles, int playersNeeded, Instant playTimeStart, Instant playTimeEnd,
                          boolean micRequired, String description, ListingStatus status, Instant expiresAt,
                          Instant createdAt, Instant updatedAt, Instant closedAt, long version) {
}
