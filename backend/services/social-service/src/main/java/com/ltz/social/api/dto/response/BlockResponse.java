package com.ltz.social.api.dto.response;

import java.time.Instant;
import java.util.UUID;

import com.ltz.social.application.service.BlockView;

public record BlockResponse(UUID blockedUserId, Instant createdAt) {

    public static BlockResponse from(BlockView view) {
        return new BlockResponse(view.blockedUserId(), view.createdAt());
    }
}
