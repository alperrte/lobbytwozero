package com.ltz.auth.domain.enums;

/** JWT "amr" değerleri (RFC 8176). */
public enum AuthMethod {
    PWD("pwd"),
    OTP("otp"),
    RECOVERY_CODE("rc");

    private final String claimValue;

    AuthMethod(String claimValue) {
        this.claimValue = claimValue;
    }

    public String claimValue() {
        return claimValue;
    }
}
