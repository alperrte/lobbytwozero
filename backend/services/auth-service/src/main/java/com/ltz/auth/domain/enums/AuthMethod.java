package com.ltz.auth.domain.enums;

/** JWT "amr" değerleri (RFC 8176; harici sağlayıcı girişi için "fed"). */
public enum AuthMethod {
    PWD("pwd"),
    EXTERNAL("fed"),
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
