package com.ltz.auth.api.dto.request;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Parola belirleme kodu isteği. TOTP etkinse TOTP kodu veya kurtarma kodu (yalnız biri) gerekir; etkin değilse boş
 * gövde ({@code {}}) yeterlidir.
 */
public record RequestPasswordSetRequest(
        @Pattern(regexp = SecondFactor.TOTP_PATTERN) String code,
        @Size(max = SecondFactor.RECOVERY_CODE_MAX_LENGTH) String recoveryCode) {

    @AssertTrue
    public boolean isAtMostOneSecondFactor() {
        return code == null || recoveryCode == null || recoveryCode.isBlank();
    }

    @Override
    public String toString() {
        return "RequestPasswordSetRequest[code=***, recoveryCode=***]";
    }
}
