package com.ltz.user.api.dto.request;

import java.util.List;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * Profil güncelleme gövdesi (PATCH). Gönderilmeyen ya da {@code null} gönderilen alan değişmez; metin alanında boş
 * metin ({@code ""}), liste alanında boş liste ({@code []}) alanı temizler. Boyut sınırları yalnız aşırı büyük
 * girdiyi erken keser; asıl kurallar (karakter denetimi, uzunluk, bilinen değerler) sunucuda uygulanır. Tanımsız alan
 * reddedilir.
 *
 * @param version istemcinin son gördüğü profil sürümü; başka bir değişiklik arada olduysa 409 döner
 */
public record UpdateProfileRequest(
        @NotNull @PositiveOrZero Long version,
        @Size(max = 256) String displayName,
        @Size(max = 4000) String bio,
        @Size(max = 8) String country,
        @Size(max = 20) List<@Size(max = 64) String> playerTypes,
        @Size(max = 20) List<@Size(max = 64) String> favoriteGenres,
        @Size(max = 20) List<@Size(max = 64) String> favoritePlatforms) {
}
