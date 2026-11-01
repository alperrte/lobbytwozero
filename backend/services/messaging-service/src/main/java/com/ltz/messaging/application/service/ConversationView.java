package com.ltz.messaging.application.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.ltz.messaging.domain.enums.ConversationType;
import com.ltz.messaging.domain.enums.MemberRole;

/** Sohbet ve aktif üyeleri, isteyen üyenin gözünden ({@code myRole}). */
public record ConversationView(UUID id, ConversationType type, String title, UUID createdBy, Instant createdAt,
                               MemberRole myRole, List<MemberView> members) {
}
