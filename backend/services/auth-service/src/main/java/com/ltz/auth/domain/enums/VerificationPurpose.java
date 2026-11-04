package com.ltz.auth.domain.enums;

public enum VerificationPurpose {
    EMAIL_VERIFICATION,
    EMAIL_CHANGE,
    /** Parolasız (Google/Steam) hesabın ilk parolasını belirlemesi; kod hesabın doğrulanmış adresine gider. */
    PASSWORD_SET
}
