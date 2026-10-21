package com.ltz.game.api.dto.response;

import com.ltz.game.domain.enums.GameStatus;
import com.ltz.game.domain.enums.OverridableField;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Yönetim görünümü: durum ve sürüm ile birlikte ortak detay yanıtı ve manuel olarak kilitlenmiş alanlar. */
public record AdminGameResponse(UUID gameId, GameStatus status, long version, Instant updatedAt, GameDetailResponse game,
        List<ManualOverride> manualOverrides) {
    public record ManualOverride(OverridableField field, Instant markedAt) { }
}
