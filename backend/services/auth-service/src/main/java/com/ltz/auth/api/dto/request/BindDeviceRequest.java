package com.ltz.auth.api.dto.request;

import com.ltz.auth.domain.entity.Account;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Cihaz bağlama: yeniden doğrulama (mevcut parola; TOTP etkinse TOTP kodu veya kurtarma kodu, yalnız biri) ve cihaz adı.
 * Ad ekranda gösterilir; biçim işaretleme karakterlerine ({@code < > & "}) izin vermez.
 */
public record BindDeviceRequest(
        @NotBlank @Size(max = 64) @Pattern(regexp = "^[\\p{L}\\p{N} ._()-]+$") String label,
        @NotNull @Size(max = Account.PASSWORD_MAX_LENGTH) String password,
        @Pattern(regexp = SecondFactor.TOTP_PATTERN) String code,
        @Size(max = SecondFactor.RECOVERY_CODE_MAX_LENGTH) String recoveryCode) {

    @AssertTrue
    public boolean isAtMostOneSecondFactor() {
        return code == null || recoveryCode == null || recoveryCode.isBlank();
    }

    @Override
    public String toString() {
        return "BindDeviceRequest[label=" + label + ", password=***, code=***, recoveryCode=***]";
    }
}
