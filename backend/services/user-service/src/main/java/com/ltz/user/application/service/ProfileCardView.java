package com.ltz.user.application.service;

import java.util.UUID;

/** Liste kartı (arama sonucu, arkadaş listesi): yalnız temel kart alanları ve avatar. */
public record ProfileCardView(UUID userId, String username, String displayName, MediaView avatar) {
}
