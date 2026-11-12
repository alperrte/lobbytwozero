package com.ltz.auth.domain.enums;

/** Yaş doğrulamasını geri alan taraf. {@code ADMIN} geri almaları kullanıcının yeniden beyanını engeller. */
public enum AgeVerificationRevoker {
    SELF,
    ADMIN
}
