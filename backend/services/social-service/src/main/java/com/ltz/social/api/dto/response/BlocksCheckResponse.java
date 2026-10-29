package com.ltz.social.api.dto.response;

import java.util.List;
import java.util.UUID;

import com.ltz.social.application.service.BlockCheckResult;

public record BlocksCheckResponse(List<Item> results) {

    /** {@code userId}: sorgulanan diğer kullanıcı; {@code blocked}: sorgulayan kullanıcıyla arasında engel var mı. */
    public record Item(UUID userId, boolean blocked) {
    }

    public static BlocksCheckResponse from(List<BlockCheckResult> results) {
        return new BlocksCheckResponse(results.stream()
                .map(result -> new Item(result.userId(), result.blocked()))
                .toList());
    }
}
