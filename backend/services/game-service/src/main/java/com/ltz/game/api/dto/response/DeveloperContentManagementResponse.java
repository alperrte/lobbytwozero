package com.ltz.game.api.dto.response;

import com.ltz.game.domain.enums.ContentStatus;
import com.ltz.game.domain.enums.DeveloperMediaType;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Yetkili geliştiricinin yönetim görünümü: durum, sürüm ve moderasyon gerekçesi dahil; silinmiş kayıtlar hariç. */
public record DeveloperContentManagementResponse(UUID gameId, Description description, List<Media> media, List<UpdateNote> updateNotes) {
    public record Description(String text, ContentStatus status, long version, String moderationReason, Instant updatedAt) { }
    public record Media(UUID id, DeveloperMediaType type, String url, String altText, ContentStatus status,
            String moderationReason, Instant createdAt) { }
    public record UpdateNote(UUID id, String title, String body, ContentStatus status, long version,
            String moderationReason, Instant createdAt, Instant updatedAt) { }
}
