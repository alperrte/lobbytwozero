package com.ltz.user.domain.exception;

/** Hız sınırı aşıldı; istemci {@code retryAfterSeconds} sonra yeniden deneyebilir ({@code Retry-After} başlığı). */
public class RateLimitedException extends UserException {

    private final long retryAfterSeconds;

    public RateLimitedException(UserErrorCode errorCode, long retryAfterSeconds) {
        super(errorCode);
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long retryAfterSeconds() {
        return retryAfterSeconds;
    }
}
