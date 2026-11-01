package com.ltz.messaging.api.dto.response;

import java.util.UUID;

import com.ltz.messaging.application.service.ReadStateView;

public record ReadStateResponse(UUID conversationId, long lastReadSeq, long unreadCount) {

    public static ReadStateResponse from(ReadStateView view) {
        return new ReadStateResponse(view.conversationId(), view.lastReadSeq(), view.unreadCount());
    }
}
