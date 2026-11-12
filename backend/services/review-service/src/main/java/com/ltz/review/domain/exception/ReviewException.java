package com.ltz.review.domain.exception;

public class ReviewException extends RuntimeException {
    private final int status;
    private final String code;

    public ReviewException(int status, String message) {
        this(status, null, message);
    }

    /** {@code code}: istemcinin aynı HTTP durumunu ayırt edebilmesi için makine-okur sabit (ör. REPORT_ALREADY_EXISTS). */
    public ReviewException(int status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public ReviewException(int status, String message, Throwable cause) {
        super(message, cause);
        this.status = status;
        this.code = null;
    }

    public int status() { return status; }

    public String code() { return code; }
}
