package com.ltz.auth.api.dto.response;

import java.time.Instant;
import java.util.UUID;

import com.ltz.auth.domain.entity.DeletionSupportRequest;

/** Yönetici görünümü: hesap kimliği ve kullanıcının notu da dahil. E-posta ve kullanıcı adı yoktur (User Service'tedir). */
public record AdminDeletionSupportResponse(UUID id, UUID accountId, String status, String message, Instant createdAt,
                                           Instant decidedAt, UUID decidedBy, String decisionNote) {

    public static AdminDeletionSupportResponse from(DeletionSupportRequest request) {
        return new AdminDeletionSupportResponse(request.getId(), request.getAccountId(), request.getStatus().name(),
                request.getMessage(), request.getCreatedAt(), request.getDecidedAt(), request.getDecidedBy(),
                request.getDecisionNote());
    }
}
