package com.ltz.social.application.service;

import java.time.Instant;
import java.util.UUID;

/** Engellenen hesap ve engelin zamanı. Profil bilgisi (ad, avatar) user-service'tedir. */
public record BlockView(UUID blockedUserId, Instant createdAt) {
}
