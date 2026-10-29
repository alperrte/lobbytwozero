package com.ltz.auth.domain.enums;

/** Cihaz kimlik bilgisinin neden iptal edildiği (denetim için). */
public enum DeviceRevokeReason {
    USER,
    PASSWORD_CHANGED,
    PASSWORD_RESET,
    ACCOUNT_INACTIVE,
    ACCOUNT_DELETION,
    EMAIL_CHANGED
}
