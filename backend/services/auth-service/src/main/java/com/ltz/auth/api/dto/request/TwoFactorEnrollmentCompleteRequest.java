package com.ltz.auth.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Zorunlu 2FA kurulumunun ikinci adımı: kurulum challenge'ı + uygulamadaki ilk TOTP kodu. */
public record TwoFactorEnrollmentCompleteRequest(
        @NotBlank @Size(max = SecondFactor.OPAQUE_TOKEN_MAX_LENGTH) String challengeId,
        @NotNull @Pattern(regexp = SecondFactor.TOTP_PATTERN) String code) {

    @Override
    public String toString() {
        return "TwoFactorEnrollmentCompleteRequest[challengeId=***, code=***]";
    }
}
