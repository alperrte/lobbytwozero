package com.ltz.auth.domain.exception;

import java.time.Duration;
import java.util.Optional;

public class AuthException extends RuntimeException {

    private final AuthErrorCode errorCode;
    private final Duration retryAfter;

    public AuthException(AuthErrorCode errorCode) {
        this(errorCode, (Duration) null);
    }

    public AuthException(AuthErrorCode errorCode, Duration retryAfter) {
        super(errorCode.name());
        this.errorCode = errorCode;
        this.retryAfter = retryAfter;
    }

    /** Özgün hatayı zincire ekler; çağıran kişisel veri sızmaması için {@link RedactedCause} geçirir. */
    public AuthException(AuthErrorCode errorCode, Throwable cause) {
        super(errorCode.name(), cause);
        this.errorCode = errorCode;
        this.retryAfter = null;
    }

    public AuthErrorCode errorCode() {
        return errorCode;
    }

    public Optional<Duration> retryAfter() {
        return Optional.ofNullable(retryAfter);
    }
}
