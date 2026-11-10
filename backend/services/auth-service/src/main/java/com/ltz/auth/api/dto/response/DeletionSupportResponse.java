package com.ltz.auth.api.dto.response;

import java.time.Instant;
import java.util.UUID;

import com.ltz.auth.domain.entity.DeletionSupportRequest;

/** Kullanıcının kendi silme talebi: yalnız durum ve zamanlar (yönetici kimliği ve karar notu dönmez). */
public record DeletionSupportResponse(UUID id, String status, Instant createdAt, Instant decidedAt) {

    public static DeletionSupportResponse from(DeletionSupportRequest request) {
        return new DeletionSupportResponse(request.getId(), request.getStatus().name(), request.getCreatedAt(),
                request.getDecidedAt());
    }
}
