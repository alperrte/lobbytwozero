package com.ltz.auth.application.service;

import java.time.Instant;

/**
 * Giriş sonucu: oturum açılır, ikinci faktör beklenir veya (zorunlu 2FA, TOTP henüz yok) önce TOTP kurulumu
 * tamamlanmalıdır. İkinci iki durumda token verilmez.
 */
public record LoginResult(Kind kind, IssuedSession session, String challengeId, Instant challengeExpiresAt) {

    public enum Kind {
        AUTHENTICATED,
        MFA_REQUIRED,
        MFA_ENROLLMENT_REQUIRED
    }

    static LoginResult authenticated(IssuedSession session) {
        return new LoginResult(Kind.AUTHENTICATED, session, null, null);
    }

    static LoginResult mfaRequired(String challengeId, Instant challengeExpiresAt) {
        return new LoginResult(Kind.MFA_REQUIRED, null, challengeId, challengeExpiresAt);
    }

    static LoginResult enrollmentRequired(String challengeId, Instant challengeExpiresAt) {
        return new LoginResult(Kind.MFA_ENROLLMENT_REQUIRED, null, challengeId, challengeExpiresAt);
    }

    /** Etkin TOTP için ikinci faktör bekleniyor. */
    public boolean requiresSecondFactor() {
        return kind == Kind.MFA_REQUIRED;
    }

    /** Hesap 2FA'ya zorunlu ve TOTP henüz kurulu değil: önce kurulum tamamlanmalı. */
    public boolean requiresEnrollment() {
        return kind == Kind.MFA_ENROLLMENT_REQUIRED;
    }

    @Override
    public String toString() {
        return "LoginResult[kind=" + kind + ", session=" + session + ", challengeId=***, challengeExpiresAt="
                + challengeExpiresAt + "]";
    }
}
