package com.ltz.performance.domain.exception;

public class PerformanceUnprocessableException extends RuntimeException {
    public static final String SYNC_CLIENT_TIME_INVALID = "SYNC_CLIENT_TIME_INVALID";
    public static final String GAME_NOT_FOUND = "GAME_NOT_FOUND";
    public static final String HARDWARE_PROFILE_NOT_FOUND = "HARDWARE_PROFILE_NOT_FOUND";
    public static final String DEVICE_LINK_NOT_FOUND = "DEVICE_LINK_NOT_FOUND";
    public static final String REPORT_EMPTY = "REPORT_EMPTY";
    public static final String REPORT_INCONSISTENT = "REPORT_INCONSISTENT";

    private final String code;

    public PerformanceUnprocessableException(String code) {
        super(code);
        this.code = code;
    }

    public String code() { return code; }
}
