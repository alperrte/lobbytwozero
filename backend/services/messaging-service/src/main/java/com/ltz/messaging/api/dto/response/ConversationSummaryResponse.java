package com.ltz.messaging.api.dto.response;

import java.time.Instant;
import java.util.UUID;

import com.ltz.messaging.application.service.ConversationSummaryView;
import com.ltz.messaging.domain.enums.ConversationType;
import com.ltz.messaging.domain.enums.MemberRole;

public record ConversationSummaryResponse(UUID id, ConversationType type, String title, UUID peerUserId,
                                          long memberCount, MemberRole myRole, Instant lastActivityAt,
                                          MessageResponse lastMessage, long unreadCount) {

    public static ConversationSummaryResponse from(ConversationSummaryView view) {
        return new ConversationSummaryResponse(view.id(), view.type(), view.title(), view.peerUserId(),
                view.memberCount(), view.myRole(), view.lastActivityAt(),
                view.lastMessage() == null ? null : MessageResponse.from(view.lastMessage()), view.unreadCount());
    }
}
