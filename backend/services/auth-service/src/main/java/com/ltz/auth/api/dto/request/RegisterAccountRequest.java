package com.ltz.auth.api.dto.request;

import com.ltz.auth.domain.entity.Account;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Rol, durum veya hesap kimliği alanı yoktur; bilinmeyen alanlar JSON ayrıştırmada reddedilir. */
public record RegisterAccountRequest(
        @NotBlank @Email @Size(max = Account.EMAIL_MAX_LENGTH) String email,
        @NotNull @Pattern(regexp = Account.USERNAME_PATTERN) String username,
        @NotNull @Size(min = Account.PASSWORD_MIN_LENGTH, max = Account.PASSWORD_MAX_LENGTH) String password) {

    @Override
    public String toString() {
        return "RegisterAccountRequest[email=***, username=" + username + ", password=***]";
    }
}
