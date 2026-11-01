package com.ltz.messaging.api.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.ltz.messaging.application.service.ConversationView;
import com.ltz.messaging.domain.enums.ConversationType;
import com.ltz.messaging.domain.enums.MemberRole;

/** Sohbet ve aktif üyeleri. {@code title} birebir sohbette {@code null}'dır. */
public record ConversationResponse(UUID id, ConversationType type, String title, UUID createdBy, Instant createdAt,
                                   MemberRole myRole, List<MemberResponse> members) {

    public static ConversationResponse from(ConversationView view) {
        return new ConversationResponse(view.id(), view.type(), view.title(), view.createdBy(), view.createdAt(),
                view.myRole(), view.members().stream().map(MemberResponse::from).toList());
    }
}
