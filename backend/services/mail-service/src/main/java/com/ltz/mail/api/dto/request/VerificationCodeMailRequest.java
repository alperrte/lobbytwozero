package com.ltz.mail.api.dto.request;

import java.util.UUID;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Auth → Mail: 6 haneli doğrulama kodu (MAIL-01). {@code locale} verilmezse e-posta iki dilli gider. */
public record VerificationCodeMailRequest(
        @NotNull UUID idempotencyKey,
        @NotNull UUID accountId,
        @NotBlank @Email @Size(max = 254) String recipientEmail,
        @NotNull @Pattern(regexp = "^[0-9]{6}$") String code,
        @NotNull @Min(60) @Max(3600) Integer validitySeconds,
        @Pattern(regexp = "^(tr|en)$") String locale) {

    @Override
    public String toString() {
        return "VerificationCodeMailRequest[idempotencyKey=" + idempotencyKey + ", recipientEmail=***, code=***]";
    }
}
