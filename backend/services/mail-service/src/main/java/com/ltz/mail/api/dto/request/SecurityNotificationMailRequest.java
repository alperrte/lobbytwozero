package com.ltz.mail.api.dto.request;

import java.time.Instant;
import java.util.UUID;

import com.ltz.mail.domain.enums.SecurityNotificationType;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Auth → Mail: parola/e-posta değişikliği ve hesap durumu bildirimleri (MAIL-03, MAIL-04). İçerik taşımaz. */
public record SecurityNotificationMailRequest(
        @NotNull UUID idempotencyKey,
        @NotNull UUID accountId,
        @NotBlank @Email @Size(max = 254) String recipientEmail,
        @NotNull SecurityNotificationType type,
        @NotNull Instant occurredAt,
        @Pattern(regexp = "^(tr|en)$") String locale) {

    @Override
    public String toString() {
        return "SecurityNotificationMailRequest[idempotencyKey=" + idempotencyKey + ", type=" + type
                + ", recipientEmail=***]";
    }
}
