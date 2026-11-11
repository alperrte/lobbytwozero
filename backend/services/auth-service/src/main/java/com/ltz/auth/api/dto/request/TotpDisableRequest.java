package com.ltz.auth.api.dto.request;

import com.ltz.auth.domain.entity.Account;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Kapatma: parola (parolası olan hesapta zorunlu) + (TOTP kodu veya kurtarma kodu). */
public record TotpDisableRequest(
        @Size(max = Account.PASSWORD_MAX_LENGTH) String password,
        @Pattern(regexp = SecondFactor.TOTP_PATTERN) String code,
        @Size(max = SecondFactor.RECOVERY_CODE_MAX_LENGTH) String recoveryCode) {

    @AssertTrue
    public boolean isExactlyOneFactor() {
        return SecondFactor.exactlyOne(code, recoveryCode);
    }

    @Override
    public String toString() {
        return "TotpDisableRequest[password=***, code=***, recoveryCode=***]";
    }
}
