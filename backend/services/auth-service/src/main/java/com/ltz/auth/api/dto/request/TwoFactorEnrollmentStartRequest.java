package com.ltz.auth.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Zorunlu 2FA kurulumunun ilk adımı: girişte verilen kurulum challenge'ı. */
public record TwoFactorEnrollmentStartRequest(
        @NotBlank @Size(max = SecondFactor.OPAQUE_TOKEN_MAX_LENGTH) String challengeId) {

    @Override
    public String toString() {
        return "TwoFactorEnrollmentStartRequest[challengeId=***]";
    }
}
