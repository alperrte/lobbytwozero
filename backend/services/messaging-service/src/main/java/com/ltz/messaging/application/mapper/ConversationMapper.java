package com.ltz.messaging.application.mapper;

import java.util.List;
import java.util.UUID;

import com.ltz.messaging.application.service.ConversationView;
import com.ltz.messaging.application.service.MemberView;
import com.ltz.messaging.domain.entity.Conversation;
import com.ltz.messaging.domain.entity.ConversationMember;
import com.ltz.messaging.domain.enums.MemberRole;

/** Entity → görünüm dönüşümü; entity API'ye dönmez. */
public final class ConversationMapper {

    private ConversationMapper() {
    }

    /**
     * @param activeMembers sohbetin aktif üyeleri (katılma sırasıyla)
     * @param viewer        görünümü isteyen aktif üye
     */
    public static ConversationView toView(Conversation conversation, List<ConversationMember> activeMembers,
                                          UUID viewer) {
        MemberRole myRole = activeMembers.stream()
                .filter(member -> member.getUserId().equals(viewer))
                .map(ConversationMember::getRole)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("The viewer is not an active member."));
        List<MemberView> members = activeMembers.stream()
                .map(member -> new MemberView(member.getUserId(), member.getRole(), member.getJoinedAt()))
                .toList();
        return new ConversationView(conversation.getId(), conversation.getType(), conversation.getTitle(),
                conversation.getCreatedBy(), conversation.getCreatedAt(), myRole, members);
    }
}
