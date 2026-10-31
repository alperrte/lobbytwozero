package com.ltz.performance.domain.exception;

public class PerformanceUnprocessableException extends RuntimeException {
    public static final String SYNC_CLIENT_TIME_INVALID = "SYNC_CLIENT_TIME_INVALID";
    public static final String GAME_NOT_FOUND = "GAME_NOT_FOUND";
    public static final String HARDWARE_PROFILE_NOT_FOUND = "HARDWARE_PROFILE_NOT_FOUND";
    public static final String DEVICE_LINK_NOT_FOUND = "DEVICE_LINK_NOT_FOUND";
    public static final String REPORT_EMPTY = "REPORT_EMPTY";
    public static final String REPORT_INCONSISTENT = "REPORT_INCONSISTENT";
    public static final String SAMPLE_METRIC_UNSUPPORTED = "SAMPLE_METRIC_UNSUPPORTED";
    public static final String SAMPLE_VALUE_INVALID = "SAMPLE_VALUE_INVALID";
    public static final String SAMPLE_TIME_INVALID = "SAMPLE_TIME_INVALID";
    public static final String SAMPLE_DUPLICATE_IN_BATCH = "SAMPLE_DUPLICATE_IN_BATCH";

    private final String code;
    private final Integer sampleIndex;

    public PerformanceUnprocessableException(String code) {
        this(code, null);
    }

    /** @param sampleIndex ihlal eden örneğin gönderimdeki sırası (0 tabanlı); örnek dışı hatalarda null */
    public PerformanceUnprocessableException(String code, Integer sampleIndex) {
        super(code);
        this.code = code;
        this.sampleIndex = sampleIndex;
    }

    public String code() { return code; }

    public Integer sampleIndex() { return sampleIndex; }
}
