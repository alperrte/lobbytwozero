package com.ltz.lfp.application.mapper;

import java.time.Instant;

import com.ltz.lfp.application.service.ListingView;
import com.ltz.lfp.domain.entity.Listing;

/** Entity → görünüm dönüşümü. Roller tembel yüklendiğinden transaction içinde çağrılmalıdır. */
public final class ListingMapper {

    private ListingMapper() {
    }

    /** @param now okuma anı: süresi geçmiş ama henüz işaretlenmemiş açık ilan {@code EXPIRED} gösterilir */
    public static ListingView toView(Listing listing, Instant now) {
        return new ListingView(listing.getId(), listing.getOwnerId(), listing.getGameId(), listing.getGameTitle(),
                listing.getPlatform(), listing.getSkillLevel(), listing.getRoles(), listing.getPlayersNeeded(),
                listing.getPlayTimeStart(), listing.getPlayTimeEnd(), listing.isMicRequired(),
                listing.getDescription(), listing.statusAt(now), listing.getExpiresAt(), listing.getCreatedAt(),
                listing.getUpdatedAt(), listing.getClosedAt(), listing.getVersion());
    }
}
