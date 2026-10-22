package com.ltz.social.domain.exception;

/** Social API'nin istemciye dönen hata sözleşmesi (HTTP durumu + kod + güvenli açıklama). */
public enum SocialErrorCode {
    CANNOT_REQUEST_SELF(400, "A friend request cannot be sent to yourself."),
    INVALID_CURSOR(400, "The pagination cursor is invalid."),
    INVALID_PAGE_SIZE(400, "The page size is out of range."),
    FRIEND_REQUEST_NOT_FOUND(404, "Friend request was not found."),
    FRIENDSHIP_NOT_FOUND(404, "You are not friends with this user."),
    INVITE_CODE_NOT_FOUND(404, "Invite code was not found or can no longer be used."),
    ALREADY_FRIENDS(409, "You are already friends with this user."),
    FRIEND_REQUEST_ALREADY_PENDING(409, "A friend request to this user is already pending."),
    INCOMING_FRIEND_REQUEST_PENDING(409, "This user has already sent you a friend request. Respond to it instead."),
    FRIEND_REQUEST_NOT_PENDING(409, "The friend request has already been answered or cancelled."),
    CONCURRENT_MODIFICATION(409, "The resource was modified concurrently. Retry the request."),
    TOO_MANY_PENDING_REQUESTS(429, "Too many pending friend requests. Wait for responses or cancel some requests.");

    private final int httpStatus;
    private final String detail;

    SocialErrorCode(int httpStatus, String detail) {
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
