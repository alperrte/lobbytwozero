package com.ltz.user.api.dto.request;

import java.util.List;

import com.ltz.user.domain.enums.Theme;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * İlk kurulum seçimleri. Favori tür ve platform listeleri zorunludur (sunucuda en az birer tane); oyuncu türü, dil ve
 * tema isteğe bağlıdır. Tanımsız alan reddedilir.
 *
 * @param version onboarding durum ucunun döndüğü profil sürümü
 */
public record CompleteOnboardingRequest(
        @NotNull @PositiveOrZero Long version,
        @Size(max = 20) List<@Size(max = 64) String> playerTypes,
        @NotNull @Size(max = 20) List<@Size(max = 64) String> favoriteGenres,
        @NotNull @Size(max = 20) List<@Size(max = 64) String> favoritePlatforms,
        @Size(max = 16) String locale,
        Theme theme) {
}
