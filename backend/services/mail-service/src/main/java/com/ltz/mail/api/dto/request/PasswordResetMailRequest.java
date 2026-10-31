package com.ltz.mail.api.dto.request;

import java.util.UUID;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Auth → Mail: parola sıfırlama bağlantısı (MAIL-02). Bağlantı politikası serviste uygulanır. */
public record PasswordResetMailRequest(
        @NotNull UUID idempotencyKey,
        @NotNull UUID accountId,
        @NotBlank @Email @Size(max = 254) String recipientEmail,
        @NotBlank @Size(max = 512) String resetLink,
        @NotNull @Min(60) @Max(86400) Integer validitySeconds,
        @Pattern(regexp = "^(tr|en)$") String locale) {

    @Override
    public String toString() {
        return "PasswordResetMailRequest[idempotencyKey=" + idempotencyKey + ", recipientEmail=***, resetLink=***]";
    }
}
