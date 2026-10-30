package com.ltz.user.application.service;

import java.util.List;

import com.ltz.user.domain.enums.Theme;

/**
 * İlk kurulum seçimleri. {@code playerTypes}, {@code locale} ve {@code theme} isteğe bağlıdır ({@code null}: değişmez ya da
 * varsayılan). Favori tür ve platform listeleri zorunludur ve boş olamaz.
 */
public record CompleteOnboardingCommand(long expectedVersion, List<String> playerTypes, List<String> favoriteGenres,
                                        List<String> favoritePlatforms, String locale, Theme theme) {
}
