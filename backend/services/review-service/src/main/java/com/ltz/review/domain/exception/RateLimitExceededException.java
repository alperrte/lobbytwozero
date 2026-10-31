package com.ltz.review.domain.exception;

public class RateLimitExceededException extends ReviewException {
    private final long retryAfterSeconds;

    public RateLimitExceededException(long retryAfterSeconds) {
        super(429, "Çok fazla istek gönderdiniz. Lütfen bekleyip yeniden deneyin.");
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long retryAfterSeconds() { return retryAfterSeconds; }
}
