package com.ltz.auth.api.dto.request;

import com.ltz.auth.domain.entity.Account;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ResetPasswordRequest(
        @NotBlank @Size(max = SecondFactor.OPAQUE_TOKEN_MAX_LENGTH) String token,
        @NotNull @Size(min = Account.PASSWORD_MIN_LENGTH, max = Account.PASSWORD_MAX_LENGTH) String newPassword,
        @NotNull @Size(max = Account.PASSWORD_MAX_LENGTH) String newPasswordConfirmation) {

    @AssertTrue
    public boolean isNewPasswordConfirmed() {
        return newPassword == null || newPassword.equals(newPasswordConfirmation);
    }

    @Override
    public String toString() {
        return "ResetPasswordRequest[token=***, newPassword=***, newPasswordConfirmation=***]";
    }
}
