package com.ltz.social.api.dto.response;

import java.time.Instant;

import com.ltz.social.application.service.InviteCodeView;

public record InviteCodeResponse(String code, Instant createdAt, Instant expiresAt, int maxUses, int usedCount) {

    public static InviteCodeResponse from(InviteCodeView view) {
        return new InviteCodeResponse(view.code(), view.createdAt(), view.expiresAt(), view.maxUses(),
                view.usedCount());
    }
}
