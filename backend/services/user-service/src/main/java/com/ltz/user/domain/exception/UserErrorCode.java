package com.ltz.user.domain.exception;

/** User API'nin istemciye dönen hata sözleşmesi (HTTP durumu + kod + güvenli açıklama). */
public enum UserErrorCode {
    INVALID_DISPLAY_NAME(400, "The display name is invalid."),
    INVALID_BIO(400, "The biography is invalid."),
    INVALID_PLAYER_TYPES(400, "The player types are invalid."),
    INVALID_FAVORITE_GENRES(400, "The favorite genres are invalid."),
    INVALID_FAVORITE_PLATFORMS(400, "The favorite platforms are invalid."),
    INVALID_COUNTRY(400, "The country is invalid."),
    INVALID_EVENT(400, "The event is invalid."),
    EVENT_PRODUCER_MISMATCH(400, "The event type does not belong to this producer."),
    CONCURRENT_MODIFICATION(409, "The resource was modified concurrently. Reload it and retry the request.");

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
