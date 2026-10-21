package com.ltz.auth.api.dto.request;

import com.ltz.auth.domain.entity.Account;
import com.ltz.auth.domain.entity.EmailVerificationCode;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ConfirmEmailVerificationRequest(
        @NotBlank @Email @Size(max = Account.EMAIL_MAX_LENGTH) String email,
        @NotNull @Pattern(regexp = EmailVerificationCode.CODE_PATTERN) String code) {

    @Override
    public String toString() {
        return "ConfirmEmailVerificationRequest[email=***, code=***]";
    }
}
