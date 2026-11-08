package com.ltz.game.api.dto.response;

import java.util.UUID;

/**
 * DLC ilişkisi. DLC için {@code parent} güvenilir ana oyunu (aktif MAIN_GAME) taşır; ilişki bilinmiyorsa {@code null} (uydurulmaz)
 * ve {@code dlcCount} null'dır. Ana oyun için {@code parent} null, {@code dlcCount} aktif DLC sayısıdır. Diğer türlerde alan yoktur.
 */
public record GameRelationResponse(Parent parent, Long dlcCount) {
    public record Parent(UUID gameId, String title, String coverUrl) { }
}
