package com.ltz.messaging.api.dto.response;

import java.time.Instant;
import java.util.UUID;

import com.ltz.messaging.application.service.MemberView;
import com.ltz.messaging.domain.enums.MemberRole;

public record MemberResponse(UUID userId, MemberRole role, Instant joinedAt) {

    public static MemberResponse from(MemberView view) {
        return new MemberResponse(view.userId(), view.role(), view.joinedAt());
    }
}
