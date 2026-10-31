package com.ltz.mail.application.mapper;

import com.ltz.mail.api.dto.request.AccountDeletionMailRequest;
import com.ltz.mail.api.dto.request.PasswordResetMailRequest;
import com.ltz.mail.api.dto.request.SecurityNotificationMailRequest;
import com.ltz.mail.api.dto.request.VerificationCodeMailRequest;
import com.ltz.mail.application.service.MailCommand;
import com.ltz.mail.application.service.MailPayload;
import com.ltz.mail.domain.enums.MailLocale;
import com.ltz.mail.domain.enums.MailType;

/** İstek DTO'su → kuyruğa alma komutu. Alan doğrulaması DTO'da (Bean Validation), bağlantı politikası serviste yapılır. */
public final class MailRequestMapper {

    private MailRequestMapper() {
    }

    public static MailCommand toCommand(VerificationCodeMailRequest request) {
        return new MailCommand(request.idempotencyKey(), request.accountId(), MailType.EMAIL_VERIFICATION,
                request.recipientEmail(), MailLocale.fromCode(request.locale()),
                new MailPayload(request.code(), null, request.validitySeconds(), null));
    }

    public static MailCommand toCommand(PasswordResetMailRequest request) {
        return new MailCommand(request.idempotencyKey(), request.accountId(), MailType.PASSWORD_RESET_LINK,
                request.recipientEmail(), MailLocale.fromCode(request.locale()),
                new MailPayload(null, request.resetLink(), request.validitySeconds(), null));
    }

    public static MailCommand toCommand(AccountDeletionMailRequest request) {
        return new MailCommand(request.idempotencyKey(), request.accountId(), MailType.ACCOUNT_DELETION_CONFIRMATION,
                request.recipientEmail(), MailLocale.fromCode(request.locale()),
                new MailPayload(null, request.confirmationLink(), request.validitySeconds(), null));
    }

    public static MailCommand toCommand(SecurityNotificationMailRequest request) {
        return new MailCommand(request.idempotencyKey(), request.accountId(), request.type().mailType(),
                request.recipientEmail(), MailLocale.fromCode(request.locale()),
                new MailPayload(null, null, null, request.occurredAt()));
    }
}
