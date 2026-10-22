package com.ltz.auth.api.dto.request;

import com.ltz.auth.domain.entity.Account;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Açık kullanıcı onayı: e-postadaki token ve (GitHub tarzı) kullanıcı adının yazılması. */
public record ConfirmAccountDeletionRequest(
        @NotBlank @Size(max = SecondFactor.OPAQUE_TOKEN_MAX_LENGTH) String token,
        @NotBlank @Size(max = Account.USERNAME_MAX_LENGTH) String username) {

    @Override
    public String toString() {
        return "ConfirmAccountDeletionRequest[token=***, username=***]";
    }
}
