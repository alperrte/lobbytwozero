package com.ltz.auth.api.dto.request;

import com.ltz.auth.infrastructure.security.TotpCodes;

/** İkinci faktör alanlarının ortak doğrulama kuralları. */
final class SecondFactor {

    static final String TOTP_PATTERN = "^[0-9]{" + TotpCodes.DIGITS + "}$";
    /** Opaque değerler (challenge, kayıt ve sıfırlama token'ı) 32 baytın Base64url karşılığıdır (43 karakter); üst sınır gevşek tutulur. */
    static final int OPAQUE_TOKEN_MAX_LENGTH = 64;
    static final int RECOVERY_CODE_MAX_LENGTH = 16;

    private SecondFactor() {
    }

    static boolean exactlyOne(String code, String recoveryCode) {
        return (code == null) != (recoveryCode == null || recoveryCode.isBlank());
    }
}
