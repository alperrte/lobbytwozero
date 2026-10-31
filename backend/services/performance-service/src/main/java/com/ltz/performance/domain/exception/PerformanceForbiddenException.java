package com.ltz.performance.domain.exception;

/** Kimliği doğrulanmış çağıranın bu işlem için yetkisi yok; kod sızıntı yaratmayacak kadar genel tutulur. */
public class PerformanceForbiddenException extends RuntimeException {
    public static final String DEVICE_NOT_LINKED = "DEVICE_NOT_LINKED";
    public static final String SYNC_CONSENT_REQUIRED = "SYNC_CONSENT_REQUIRED";

    private final String code;

    public PerformanceForbiddenException(String code) {
        super(code);
        this.code = code;
    }

    public String code() { return code; }
}
