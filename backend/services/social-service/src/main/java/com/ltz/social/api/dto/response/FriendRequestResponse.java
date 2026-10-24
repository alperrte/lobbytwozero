package com.ltz.social.api.dto.response;

import java.time.Instant;
import java.util.UUID;

import com.ltz.social.application.service.FriendRequestView;
import com.ltz.social.domain.enums.FriendRequestStatus;

public record FriendRequestResponse(UUID id, UUID senderId, UUID receiverId, FriendRequestStatus status,
                                    Instant createdAt, Instant respondedAt) {

    public static FriendRequestResponse from(FriendRequestView view) {
        return new FriendRequestResponse(view.id(), view.senderId(), view.receiverId(), view.status(),
                view.createdAt(), view.respondedAt());
    }
}
