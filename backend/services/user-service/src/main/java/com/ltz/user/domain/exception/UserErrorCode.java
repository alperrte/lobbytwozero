package com.ltz.user.domain.exception;

/** User API'nin istemciye dönen hata sözleşmesi (HTTP durumu + kod + güvenli açıklama). */
public enum UserErrorCode {
    INVALID_DISPLAY_NAME(400, "The display name is invalid."),
    INVALID_BIO(400, "The biography is invalid."),
    INVALID_PLAYER_TYPES(400, "The player types are invalid."),
    INVALID_FAVORITE_GENRES(400, "The favorite genres are invalid."),
    INVALID_FAVORITE_PLATFORMS(400, "The favorite platforms are invalid."),
    INVALID_COUNTRY(400, "The country is invalid."),
    INVALID_LOCALE(400, "The locale must be one of: tr, en."),
    ONBOARDING_INCOMPLETE(400, "Select at least one favorite genre and one favorite platform to finish onboarding."),
    INVALID_EVENT(400, "The event is invalid."),
    INVALID_SEARCH_QUERY(400, "The search query must be 2-32 characters without control characters."),
    PROFILE_NOT_FOUND(404, "The profile was not found."),
    EVENT_PRODUCER_MISMATCH(400, "The event type does not belong to this producer."),
    INVALID_IMAGE(400, "The image is invalid, corrupted or its type does not match its content."),
    INVALID_IMAGE_DIMENSIONS(400, "The image dimensions or frame count are outside the allowed limits."),
    MEDIA_NOT_FOUND(404, "The requested media was not found."),
    CONCURRENT_MODIFICATION(409, "The resource was modified concurrently. Reload it and retry the request."),
    MEDIA_RATE_LIMITED(429, "Too many image uploads. Try again later."),
    MEDIA_BUSY(503, "The image processor is busy. Try again shortly."),
    MEDIA_STORAGE_UNAVAILABLE(503, "Media storage is temporarily unavailable. Try again shortly."),
    ACCOUNT_ERASURE_PENDING(503, "Account data erasure is not finished yet. Retry later.");

    private final int httpStatus;
    private final String detail;

    UserErrorCode(int httpStatus, String detail) {
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
