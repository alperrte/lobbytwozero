package com.ltz.review.api.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Moderatör görünümü: her durumdaki (silinmiş hariç metinli) inceleme ve moderasyon geçmişi. Yazar kimliği dönmez. */
public record AdminReviewResponse(UUID id, UUID gameId, String body, String status, long version, Instant createdAt, Instant updatedAt,
        List<Entry> history) {
    public AdminReviewResponse { history = List.copyOf(history); }

    /** Denetim kaydı satırı; moderatör kimliği dönmez (kayıtta saklanır). */
    public record Entry(String action, String reason, long reviewVersion, Instant createdAt) {}
}
