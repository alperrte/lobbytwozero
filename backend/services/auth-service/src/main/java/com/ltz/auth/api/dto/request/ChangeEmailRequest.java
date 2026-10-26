package com.ltz.auth.api.dto.request;

import com.ltz.auth.domain.entity.Account;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Yeniden doğrulama: mevcut parola; TOTP etkinse TOTP kodu veya kurtarma kodu (yalnız biri). Yeni adrese kod gider,
 * hesap adresi kod onaylanana kadar değişmez.
 */
public record ChangeEmailRequest(
        @NotBlank @Email @Size(max = Account.EMAIL_MAX_LENGTH) String newEmail,
        @NotNull @Size(max = Account.PASSWORD_MAX_LENGTH) String password,
        @Pattern(regexp = SecondFactor.TOTP_PATTERN) String code,
        @Size(max = SecondFactor.RECOVERY_CODE_MAX_LENGTH) String recoveryCode) {

    @AssertTrue
    public boolean isAtMostOneSecondFactor() {
        return code == null || recoveryCode == null || recoveryCode.isBlank();
    }

    @Override
    public String toString() {
        return "ChangeEmailRequest[newEmail=***, password=***, code=***, recoveryCode=***]";
    }
}
