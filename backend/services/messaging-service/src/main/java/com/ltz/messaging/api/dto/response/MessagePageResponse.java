package com.ltz.messaging.api.dto.response;

import java.util.List;

import com.ltz.messaging.application.service.MessagePageView;

public record MessagePageResponse(List<MessageResponse> items, boolean hasMore) {

    public static MessagePageResponse from(MessagePageView view) {
        return new MessagePageResponse(view.items().stream().map(MessageResponse::from).toList(), view.hasMore());
    }
}
