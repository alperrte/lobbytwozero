package com.ltz.auth.api.dto.request;

import com.ltz.auth.domain.entity.Account;
import com.ltz.auth.domain.entity.EmailVerificationCode;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Parolalar dönüştürülmez veya kırpılmaz; yeni parola ve tekrarı birebir eşleşmelidir. */
public record SetPasswordRequest(
        @NotNull @Pattern(regexp = EmailVerificationCode.CODE_PATTERN) String emailCode,
        @NotNull @Size(min = Account.PASSWORD_MIN_LENGTH, max = Account.PASSWORD_MAX_LENGTH) String newPassword,
        @NotNull @Size(max = Account.PASSWORD_MAX_LENGTH) String newPasswordConfirmation) {

    @AssertTrue
    public boolean isNewPasswordConfirmed() {
        return newPassword == null || newPassword.equals(newPasswordConfirmation);
    }

    @Override
    public String toString() {
        return "SetPasswordRequest[emailCode=***, newPassword=***, newPasswordConfirmation=***]";
    }
}
