package com.ltz.lfp.api.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.ltz.lfp.application.service.ListingView;
import com.ltz.lfp.domain.enums.ListingStatus;
import com.ltz.lfp.domain.enums.Platform;

/**
 * İlan. Sahip yalnız kimliğiyle döner; görünen ad/avatar user-service'in toplu profil kartından alınır (LFP içinde
 * kişisel veri tutulmaz). {@code gameTitle} ilan açılırken alınan anlık görüntüdür. {@code version} güncelleme
 * isteğinde geri gönderilir (eşzamanlı değişiklik 409).
 */
public record ListingResponse(UUID id, UUID ownerId, UUID gameId, String gameTitle, Platform platform,
                              String skillLevel, List<String> roles, int playersNeeded, Instant playTimeStart,
                              Instant playTimeEnd, boolean micRequired, String description, ListingStatus status,
                              Instant expiresAt, Instant createdAt, Instant updatedAt, Instant closedAt,
                              long version) {

    public static ListingResponse from(ListingView view) {
        return new ListingResponse(view.id(), view.ownerId(), view.gameId(), view.gameTitle(), view.platform(),
                view.skillLevel(), view.roles(), view.playersNeeded(), view.playTimeStart(), view.playTimeEnd(),
                view.micRequired(), view.description(), view.status(), view.expiresAt(), view.createdAt(),
                view.updatedAt(), view.closedAt(), view.version());
    }
}
