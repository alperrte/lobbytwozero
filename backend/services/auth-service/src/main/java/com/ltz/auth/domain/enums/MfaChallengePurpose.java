package com.ltz.auth.domain.enums;

/** MFA challenge'ının amacı: etkin TOTP ile giriş ikinci adımı veya zorunlu 2FA'nın ilk kurulumu. */
public enum MfaChallengePurpose {
    LOGIN,
    ENROLLMENT
}
