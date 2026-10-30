package com.ltz.user.domain.exception;

public class UserException extends RuntimeException {

    private final UserErrorCode errorCode;

    public UserException(UserErrorCode errorCode) {
        super(errorCode.name());
        this.errorCode = errorCode;
    }

    public UserErrorCode errorCode() {
        return errorCode;
    }
}
