package com.ltz.messaging.application.service;

import java.time.Instant;
import java.util.UUID;

import com.ltz.messaging.domain.enums.MemberRole;

/** Aktif bir sohbet üyesi. Ad/avatar user-service'tedir; burada yalnız kimlik döner. */
public record MemberView(UUID userId, MemberRole role, Instant joinedAt) {
}
