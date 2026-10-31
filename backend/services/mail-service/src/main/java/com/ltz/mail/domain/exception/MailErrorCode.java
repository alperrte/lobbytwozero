package com.ltz.mail.domain.exception;

/** Mail iç API'sinin istemciye dönen hata sözleşmesi (HTTP durumu + kod + güvenli açıklama). */
public enum MailErrorCode {
    INVALID_LINK(400, "The link is not allowed."),
    INVALID_OCCURRED_AT(400, "The occurredAt value is not acceptable."),
    IDEMPOTENCY_CONFLICT(409, "The idempotency key was already used with a different request."),
    TEMPORARILY_UNAVAILABLE(503, "The mail request could not be recorded. Retry shortly.");

    private final int httpStatus;
    private final String detail;

    MailErrorCode(int httpStatus, String detail) {
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
