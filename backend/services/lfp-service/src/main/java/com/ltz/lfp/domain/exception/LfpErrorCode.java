package com.ltz.lfp.domain.exception;

/** LFP API'nin istemciye dönen hata sözleşmesi (HTTP durumu + kod + güvenli açıklama). */
public enum LfpErrorCode {
    INVALID_DESCRIPTION(400, "The description must contain 1 to 1000 visible characters."),
    INVALID_SKILL_LEVEL(400, "The skill level must contain 1 to 50 visible characters on a single line."),
    INVALID_ROLES(400, "Between 1 and 5 distinct roles of 1 to 30 characters each are required."),
    INVALID_DURATION(400, "The listing duration is out of the allowed range."),
    INVALID_PLAY_TIME(400, "The planned play time is invalid."),
    PLAY_TIME_AFTER_EXPIRY(400, "The planned play time must start before the listing expires."),
    INVALID_FILTER(400, "A search filter is invalid."),
    INVALID_CURSOR(400, "The pagination cursor is invalid."),
    INVALID_PAGE_SIZE(400, "The page size is out of range."),
    NOT_LISTING_OWNER(403, "Only the owner can change this listing."),
    LISTING_NOT_FOUND(404, "Listing was not found."),
    LISTING_NOT_OPEN(409, "The listing is closed or expired."),
    GAME_NOT_FOUND(422, "The game was not found in the LTZ catalog."),
    GAME_NOT_PLAYABLE(422, "Listings can only be created for games."),
    OPEN_LISTING_LIMIT(409, "You have reached the maximum number of open listings. Close one first."),
    CONCURRENT_MODIFICATION(409, "The resource was modified concurrently. Retry the request."),
    LISTING_RATE_LIMITED(429, "Too many listings were created recently. Try again later."),
    GAME_CATALOG_UNAVAILABLE(503, "The game catalog is temporarily unavailable. Try again later.");

    private final int httpStatus;
    private final String detail;

    LfpErrorCode(int httpStatus, String detail) {
        this.httpStatus = httpStatus;
        this.detail = detail;
    }

    public int httpStatus() {
        return httpStatus;
    }

    public String detail() {
        return detail;
    }
}
