package com.ltz.auth.api.dto.request;

import com.ltz.auth.domain.entity.EmailVerificationCode;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/** Yeni adrese gönderilen 6 haneli kod. */
public record ConfirmEmailChangeRequest(@NotNull @Pattern(regexp = EmailVerificationCode.CODE_PATTERN) String code) {

    @Override
    public String toString() {
        return "ConfirmEmailChangeRequest[code=***]";
    }
}
