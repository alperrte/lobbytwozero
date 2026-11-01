package com.ltz.messaging.domain.exception;

public class MessagingException extends RuntimeException {

    private final MessagingErrorCode errorCode;

    public MessagingException(MessagingErrorCode errorCode) {
        super(errorCode.name());
        this.errorCode = errorCode;
    }

    public MessagingException(MessagingErrorCode errorCode, Throwable cause) {
        super(errorCode.name(), cause);
        this.errorCode = errorCode;
    }

    public MessagingErrorCode errorCode() {
        return errorCode;
    }
}
