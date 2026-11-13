package com.ltz.lfp.application.mapper;

import com.ltz.lfp.application.service.ListingView;
import com.ltz.lfp.domain.entity.Listing;

/** Entity → görünüm dönüşümü. Roller tembel yüklendiğinden transaction içinde çağrılmalıdır. */
public final class ListingMapper {

    private ListingMapper() {
    }

    public static ListingView toView(Listing listing) {
        return new ListingView(listing.getId(), listing.getOwnerId(), listing.getGameId(), listing.getGameTitle(),
                listing.getPlatform(), listing.getSkillLevel(), listing.getRoles(), listing.getPlayersNeeded(),
                listing.getPlayTimeStart(), listing.getPlayTimeEnd(), listing.isMicRequired(),
                listing.getDescription(), listing.getStatus(), listing.getExpiresAt(), listing.getCreatedAt(),
                listing.getUpdatedAt(), listing.getClosedAt());
    }
}
