package com.ltz.game.api.dto.response;

import java.util.List;
import java.util.UUID;

/**
 * Recommendation için herkese açık teknik metadata. Her istenen kimlik tam bir kez {@code items} ya da
 * {@code unavailable} içindedir; pasif, mağazadan kaldırılmış, yetişkin işaretli ve bilinmeyen oyunlar ayırt edilemez.
 */
public record RecommendationMetadataBatchResponse(List<Item> items, List<UUID> unavailable) {
    public record Item(UUID gameId, String gameType, List<String> genres, List<String> platforms, List<Requirement> requirements) { }
    public record Requirement(String level, String platform, String os, String cpu, String gpu, Integer ramMb, Integer storageMb) { }
}
