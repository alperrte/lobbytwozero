package com.ltz.messaging.api.dto.request;

import com.ltz.messaging.domain.enums.MemberRole;

import jakarta.validation.constraints.NotNull;

/** Yeni rol: {@code ADMIN} ya da {@code MEMBER}. {@code OWNER} bu uçla atanamaz. */
public record ChangeMemberRoleRequest(@NotNull MemberRole role) {
}
