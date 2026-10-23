package com.ltz.social.domain.event;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import com.ltz.social.domain.entity.FriendshipId;

/**
 * Arkadaşlık kuruldu ya da bitti. Tüketiciler: messaging-service (sohbet izni), recommendation-service
 * (arkadaş önerisi). Çift sırasızdır; kimlikler {@link FriendshipId} sırasıyla (low, high) verilir.
 */
public record FriendshipChangedEvent(UUID userLowId, UUID userHighId, Change change) implements SocialEvent {

    public enum Change {
        ADDED,
        REMOVED
    }

    public static FriendshipChangedEvent of(UUID first, UUID second, Change change) {
        FriendshipId id = FriendshipId.of(first, second);
        return new FriendshipChangedEvent(id.getUserLowId(), id.getUserHighId(), change);
    }

    @Override
    public String eventType() {
        return "FriendshipChanged";
    }

    @Override
    public int eventVersion() {
        return 1;
    }

    @Override
    public String aggregateType() {
        return "Friendship";
    }

    /** Bileşik anahtarlı kaynağın tek kimliği yoktur; çiftten kararlı (sıra bağımsız) bir UUID türetilir. */
    @Override
    public UUID aggregateId() {
        return UUID.nameUUIDFromBytes((userLowId + ":" + userHighId).getBytes(StandardCharsets.UTF_8));
    }
}
