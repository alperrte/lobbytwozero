package com.ltz.auth.api.dto.request;

import com.ltz.auth.domain.entity.Account;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Steam ile yeni hesap: doğrulanmış SteamID'ye bağlı kayıt token'ı + e-posta + kullanıcı adı. Parola yoktur. */
public record SteamRegistrationRequest(
        @NotBlank @Size(max = SecondFactor.CHALLENGE_MAX_LENGTH) String registrationToken,
        @NotBlank @Email @Size(max = Account.EMAIL_MAX_LENGTH) String email,
        @NotNull @Pattern(regexp = Account.USERNAME_PATTERN) String username) {

    @Override
    public String toString() {
        return "SteamRegistrationRequest[registrationToken=***, email=***, username=" + username + "]";
    }
}
