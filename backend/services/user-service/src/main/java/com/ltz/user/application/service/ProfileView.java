package com.ltz.user.application.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Kullanıcının kendi profilinin uygulama katmanı görünümü (entity API'ye çıkmaz). Görsel yoksa {@code null}. */
public record ProfileView(UUID userId, String username, String displayName, String bio, List<String> playerTypes,
                          List<String> favoriteGenres, List<String> favoritePlatforms, String country, MediaView avatar,
                          MediaView cover, Instant onboardingCompletedAt, Instant updatedAt, long version) {
}
