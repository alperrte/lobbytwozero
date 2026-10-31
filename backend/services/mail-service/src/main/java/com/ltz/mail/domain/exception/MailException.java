package com.ltz.mail.domain.exception;

public class MailException extends RuntimeException {

    private final MailErrorCode errorCode;

    public MailException(MailErrorCode errorCode) {
        super(errorCode.name());
        this.errorCode = errorCode;
    }

    public MailErrorCode errorCode() {
        return errorCode;
    }
}
