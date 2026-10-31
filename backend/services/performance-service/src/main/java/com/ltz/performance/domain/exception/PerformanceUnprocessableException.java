package com.ltz.performance.domain.exception;

public class PerformanceUnprocessableException extends RuntimeException {
    public static final String SYNC_CLIENT_TIME_INVALID = "SYNC_CLIENT_TIME_INVALID";

    private final String code;

    public PerformanceUnprocessableException(String code) {
        super(code);
        this.code = code;
    }

    public String code() { return code; }
}
