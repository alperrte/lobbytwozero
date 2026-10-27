package com.ltz.auth.api.dto.request;

import com.ltz.auth.domain.entity.Account;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** identifier: e-posta veya kullanıcı adı. Parola biçimi burada doğrulanmaz; yalnız üst sınır uygulanır. */
public record LoginRequest(
        @NotBlank @Size(max = Account.EMAIL_MAX_LENGTH) String identifier,
        @NotNull @Size(max = Account.PASSWORD_MAX_LENGTH) String password) {

    @Override
    public String toString() {
        return "LoginRequest[identifier=***, password=***]";
    }
}
