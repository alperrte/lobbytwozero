package com.ltz.auth.domain.exception;

import java.time.Duration;
import java.util.Optional;

public class AuthException extends RuntimeException {

    private final AuthErrorCode errorCode;
    private final Duration retryAfter;

    public AuthException(AuthErrorCode errorCode) {
        this(errorCode, null);
    }

    public AuthException(AuthErrorCode errorCode, Duration retryAfter) {
        this(errorCode, retryAfter, null);
    }

    public AuthException(AuthErrorCode errorCode, Duration retryAfter, RedactedCause cause) {
        super(errorCode.name(), cause);
        this.errorCode = errorCode;
        this.retryAfter = retryAfter;
    }

    public AuthErrorCode errorCode() {
        return errorCode;
    }

    public Optional<Duration> retryAfter() {
        return Optional.ofNullable(retryAfter);
    }
}
