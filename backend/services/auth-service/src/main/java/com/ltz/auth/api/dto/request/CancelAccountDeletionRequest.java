package com.ltz.auth.api.dto.request;

import com.ltz.auth.domain.entity.Account;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Bekleme süresi içinde silmeden vazgeçme. Hesap kapalı olduğu için oturum yoktur; kimlik, girişteki gibi
 * e-posta/kullanıcı adı + parola ile doğrulanır; TOTP etkinse TOTP kodu veya kurtarma kodu (yalnız biri) eklenir.
 */
public record CancelAccountDeletionRequest(
        @NotBlank @Size(max = Account.EMAIL_MAX_LENGTH) String identifier,
        @NotNull @Size(max = Account.PASSWORD_MAX_LENGTH) String password,
        @Pattern(regexp = SecondFactor.TOTP_PATTERN) String code,
        @Size(max = SecondFactor.RECOVERY_CODE_MAX_LENGTH) String recoveryCode) {

    @AssertTrue
    public boolean isAtMostOneSecondFactor() {
        return code == null || recoveryCode == null || recoveryCode.isBlank();
    }

    @Override
    public String toString() {
        return "CancelAccountDeletionRequest[identifier=***, password=***, code=***, recoveryCode=***]";
    }
}
