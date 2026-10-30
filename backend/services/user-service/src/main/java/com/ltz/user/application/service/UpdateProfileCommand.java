package com.ltz.user.application.service;

import java.util.List;

/**
 * Profil güncelleme girdisi. {@code null} alan "değişmedi" demektir; metin alanlarında boş metin, liste alanlarında boş
 * liste alanı temizler. {@code expectedVersion} istemcinin son gördüğü sürümdür (iyimser kilit).
 */
public record UpdateProfileCommand(long expectedVersion, String displayName, String bio, String country,
                                   List<String> playerTypes, List<String> favoriteGenres,
                                   List<String> favoritePlatforms) {
}
