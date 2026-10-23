package com.ltz.social.domain.exception;

public class SocialException extends RuntimeException {

    private final SocialErrorCode errorCode;

    public SocialException(SocialErrorCode errorCode) {
        super(errorCode.name());
        this.errorCode = errorCode;
    }

    public SocialErrorCode errorCode() {
        return errorCode;
    }
}
