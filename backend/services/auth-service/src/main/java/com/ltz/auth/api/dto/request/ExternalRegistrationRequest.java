package com.ltz.auth.api.dto.request;

import com.ltz.auth.domain.entity.Account;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Google/Steam ile ilk girişte hesabı tamamlar: sağlayıcıda doğrulanmış kimliğe bağlı tek kullanımlık kayıt token'ı +
 * seçilen kullanıcı adı. E-posta ve sağlayıcı kimliği istemciden ALINMAZ; sunucu kendi doğruladığı kayıttan okur.
 */
public record ExternalRegistrationRequest(
        @NotBlank @Size(max = SecondFactor.OPAQUE_TOKEN_MAX_LENGTH) String registrationToken,
        @NotNull @Pattern(regexp = Account.USERNAME_PATTERN) String username) {

    @Override
    public String toString() {
        return "ExternalRegistrationRequest[registrationToken=***, username=" + username + "]";
    }
}
