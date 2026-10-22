package com.ltz.social.domain.event;

import java.util.UUID;

/** Arkadaşlık isteği kabul edildi. Tüketici: notification-service (isteği gönderene bildirim). */
public record FriendRequestAcceptedEvent(UUID requestId, UUID senderId, UUID receiverId) implements SocialEvent {

    @Override
    public String eventType() {
        return "FriendRequestAccepted";
    }

    @Override
    public int eventVersion() {
        return 1;
    }

    @Override
    public String aggregateType() {
        return "FriendRequest";
    }

    @Override
    public UUID aggregateId() {
        return requestId;
    }
}
