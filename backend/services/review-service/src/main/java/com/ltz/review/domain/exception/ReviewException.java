package com.ltz.review.domain.exception;

public class ReviewException extends RuntimeException {
    private final int status;

    public ReviewException(int status, String message) {
        super(message);
        this.status = status;
    }

    public ReviewException(int status, String message, Throwable cause) {
        super(message, cause);
        this.status = status;
    }

    public int status() { return status; }
}
