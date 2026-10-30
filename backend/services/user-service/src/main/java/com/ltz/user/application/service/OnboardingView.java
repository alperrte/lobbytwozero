package com.ltz.user.application.service;

import java.time.Instant;
import java.util.List;

/**
 * İlk kurulum durumu. {@code missing}: tamamlanması için eksik adımlar ({@code FAVORITE_GENRES},
 * {@code FAVORITE_PLATFORMS}); tamamlanmışsa boş. {@code profileVersion}: tamamlama isteğinde gönderilecek sürüm.
 */
public record OnboardingView(boolean completed, Instant completedAt, List<String> missing, long profileVersion) {
}
