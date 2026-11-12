package com.ltz.auth.api.dto.request;

import com.ltz.auth.domain.entity.Account;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * B/C türü hesabın silme talebi. Geri alınamaz bir işlem olduğu için yeniden doğrulama ister: parolalı hesapta parola,
 * parolasız hesapta yakın zamanlı oturum (ve TOTP zorunlu), TOTP etkinse TOTP kodu veya kurtarma kodu (yalnız biri).
 * {@code message} yöneticiye giden isteğe bağlı kısa nottur.
 */
public record DeletionSupportOpenRequest(
        @Size(max = AdminReason.MAX_LENGTH) String message,
        @Size(max = Account.PASSWORD_MAX_LENGTH) String password,
        @Pattern(regexp = SecondFactor.TOTP_PATTERN) String code,
        @Size(max = SecondFactor.RECOVERY_CODE_MAX_LENGTH) String recoveryCode) {

    public static final DeletionSupportOpenRequest EMPTY = new DeletionSupportOpenRequest(null, null, null, null);

    @AssertTrue
    public boolean isAtMostOneSecondFactor() {
        return code == null || recoveryCode == null || recoveryCode.isBlank();
    }

    @Override
    public String toString() {
        return "DeletionSupportOpenRequest[message=***, password=***, code=***, recoveryCode=***]";
    }
}
