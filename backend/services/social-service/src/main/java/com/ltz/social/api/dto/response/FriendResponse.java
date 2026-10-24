package com.ltz.social.api.dto.response;

import java.time.Instant;
import java.util.UUID;

import com.ltz.social.application.service.FriendView;

public record FriendResponse(UUID friendId, Instant since) {

    public static FriendResponse from(FriendView view) {
        return new FriendResponse(view.friendId(), view.since());
    }
}
