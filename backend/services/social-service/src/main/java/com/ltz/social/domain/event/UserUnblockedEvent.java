package com.ltz.social.domain.event;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** Engel kaldırıldı. Tüketiciler: messaging-service, lfp-service, community-service. */
public record UserUnblockedEvent(UUID blockerId, UUID blockedId) implements SocialEvent {

    @Override
    public String eventType() {
        return "UserUnblocked";
    }

    @Override
    public int eventVersion() {
        return 1;
    }

    @Override
    public String aggregateType() {
        return "UserBlock";
    }

    @Override
    public UUID aggregateId() {
        return UUID.nameUUIDFromBytes((blockerId + ">" + blockedId).getBytes(StandardCharsets.UTF_8));
    }
}
