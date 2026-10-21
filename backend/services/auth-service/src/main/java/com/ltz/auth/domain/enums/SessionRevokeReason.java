package com.ltz.auth.domain.enums;

public enum SessionRevokeReason {
    LOGOUT,
    LOGOUT_ALL,
    REFRESH_REUSE,
    ACCOUNT_INACTIVE,
    PASSWORD_CHANGED,
    PASSWORD_RESET
}
