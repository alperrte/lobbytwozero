package com.ltz.auth.api.dto.request;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Girişin ikinci adımı: challenge + (TOTP kodu veya kurtarma kodu, yalnız biri). */
public record TwoFactorLoginRequest(
        @NotBlank @Size(max = SecondFactor.CHALLENGE_MAX_LENGTH) String challengeId,
        @Pattern(regexp = SecondFactor.TOTP_PATTERN) String code,
        @Size(max = SecondFactor.RECOVERY_CODE_MAX_LENGTH) String recoveryCode) {

    @AssertTrue
    public boolean isExactlyOneFactor() {
        return SecondFactor.exactlyOne(code, recoveryCode);
    }

    @Override
    public String toString() {
        return "TwoFactorLoginRequest[challengeId=***, code=***, recoveryCode=***]";
    }
}
