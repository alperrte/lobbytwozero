package com.ltz.mail.domain.enums;

/**
 * Auth'un {@code SecurityNotificationType} değerleriyle birebir aynı ad sözleşmesi (Auth → Mail güvenlik bildirimi).
 * Bildirim parola, token, kod veya bağlantı taşımaz; yalnız ne olduğunu ve ne zaman olduğunu söyler.
 */
public enum SecurityNotificationType {
    PASSWORD_CHANGED(MailType.PASSWORD_CHANGED),
    PASSWORD_RESET(MailType.PASSWORD_RESET_COMPLETED),
    EMAIL_CHANGED(MailType.EMAIL_CHANGED),
    ACCOUNT_SUSPENDED(MailType.ACCOUNT_SUSPENDED),
    ACCOUNT_BANNED(MailType.ACCOUNT_BANNED),
    ACCOUNT_DISABLED(MailType.ACCOUNT_DISABLED),
    ACCOUNT_REACTIVATED(MailType.ACCOUNT_REACTIVATED),
    TWO_FACTOR_ENABLED(MailType.TWO_FACTOR_ENABLED),
    TWO_FACTOR_DISABLED(MailType.TWO_FACTOR_DISABLED),
    RECOVERY_CODE_USED(MailType.RECOVERY_CODE_USED),
    EXTERNAL_IDENTITY_LINKED(MailType.EXTERNAL_IDENTITY_LINKED);

    private final MailType mailType;

    SecurityNotificationType(MailType mailType) {
        this.mailType = mailType;
    }

    public MailType mailType() {
        return mailType;
    }
}
