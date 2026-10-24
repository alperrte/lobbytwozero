package com.ltz.game.api.dto.response;

import com.ltz.game.domain.enums.DeveloperMediaType;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Herkese açık geliştirici içeriği. {@code contributor=DEVELOPER}: içerik katalog/sağlayıcı verisi değil, oyunun yetkili
 * geliştiricisi tarafından eklenmiştir ve yayın öncesi onaylanmamıştır; moderasyonla gizlenebilir. Yazar kimliği dönmez.
 */
public record DeveloperContentResponse(UUID gameId, String contributor, Description description, List<Media> media,
        List<UpdateNote> updateNotes) {
    public record Description(String text, Instant updatedAt) { }
    public record Media(UUID id, DeveloperMediaType type, String url, String altText, Instant createdAt) { }
    public record UpdateNote(UUID id, String title, String body, Instant createdAt, Instant updatedAt) { }
}
