package com.ltz.messaging.api.dto.response;

import java.util.List;

import com.ltz.messaging.application.service.ConversationPageView;

public record ConversationPageResponse(List<ConversationSummaryResponse> items, String nextCursor) {

    public static ConversationPageResponse from(ConversationPageView view) {
        return new ConversationPageResponse(view.items().stream().map(ConversationSummaryResponse::from).toList(),
                view.nextCursor());
    }
}
