package com.ltz.social.domain.event;

import java.util.UUID;

/** Yeni arkadaşlık isteği oluştu. Tüketici: notification-service (alıcıya bildirim). */
public record FriendRequestSentEvent(UUID requestId, UUID senderId, UUID receiverId) implements SocialEvent {

    @Override
    public String eventType() {
        return "FriendRequestSent";
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
