package com.ltz.performance.domain.exception;

public class PerformanceConflictException extends RuntimeException {
    public static final String IDEMPOTENCY_KEY_REUSED = "IDEMPOTENCY_KEY_REUSED";
    public static final String DEVICE_LINK_CONFLICT = "DEVICE_LINK_CONFLICT";
    public static final String DEVICE_LINK_LIMIT_REACHED = "DEVICE_LINK_LIMIT_REACHED";
    public static final String SYNC_BATCH_LIMIT_REACHED = "SYNC_BATCH_LIMIT_REACHED";
    public static final String ACTIVE_SESSION_EXISTS = "ACTIVE_SESSION_EXISTS";
    public static final String SESSION_LIMIT_REACHED = "SESSION_LIMIT_REACHED";
    public static final String SESSION_ALREADY_ENDED = "SESSION_ALREADY_ENDED";
    public static final String SESSION_NOT_COMPLETED = "SESSION_NOT_COMPLETED";

    private final String code;

    public PerformanceConflictException(String code) {
        super(code);
        this.code = code;
    }

    public String code() { return code; }
}
