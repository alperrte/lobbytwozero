package com.ltz.auth.api.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record TotpActivateRequest(@NotNull @Pattern(regexp = SecondFactor.TOTP_PATTERN) String code) {

    @Override
    public String toString() {
        return "TotpActivateRequest[code=***]";
    }
}
