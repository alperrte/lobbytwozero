package com.ltz.social.application.service;

import java.time.Instant;
import java.util.UUID;

/** Arkadaş listesi satırı: arkadaşın kimliği ve arkadaşlığın başlangıcı. Profil bilgisi user-service'tedir. */
public record FriendView(UUID friendId, Instant since) {
}
