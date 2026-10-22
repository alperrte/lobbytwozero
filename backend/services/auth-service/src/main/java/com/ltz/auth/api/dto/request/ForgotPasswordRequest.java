package com.ltz.auth.api.dto.request;

import com.ltz.auth.domain.entity.Account;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ForgotPasswordRequest(@NotBlank @Email @Size(max = Account.EMAIL_MAX_LENGTH) String email) {

    @Override
    public String toString() {
        return "ForgotPasswordRequest[email=***]";
    }
}
