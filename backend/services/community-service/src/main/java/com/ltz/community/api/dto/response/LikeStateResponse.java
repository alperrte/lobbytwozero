package com.ltz.community.api.dto.response;

import java.util.UUID;

/** Çağıranın gönderi beğeni durumu ve güncel beğeni sayısı. */
public record LikeStateResponse(UUID postId, boolean liked, int likeCount) {
}
