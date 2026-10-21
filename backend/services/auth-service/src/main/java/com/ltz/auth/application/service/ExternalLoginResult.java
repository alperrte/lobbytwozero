package com.ltz.auth.application.service;

/** Harici girişin sonucu: oturum açıldı, 2FA bekleniyor veya (yalnız Steam) kayıt tamamlanmalı. */
public record ExternalLoginResult(Outcome outcome, IssuedSession session, String challengeId, String token) {

    public enum Outcome {
        AUTHENTICATED,
        MFA_REQUIRED,
        REGISTRATION_REQUIRED,
        EMAIL_VERIFICATION_REQUIRED
    }

    static ExternalLoginResult from(LoginResult login) {
        return login.requiresSecondFactor()
                ? new ExternalLoginResult(Outcome.MFA_REQUIRED, null, login.challengeId(), null)
                : new ExternalLoginResult(Outcome.AUTHENTICATED, login.session(), null, null);
    }

    static ExternalLoginResult registrationRequired(String registrationToken) {
        return new ExternalLoginResult(Outcome.REGISTRATION_REQUIRED, null, null, registrationToken);
    }

    static ExternalLoginResult emailVerificationRequired(String verificationProof) {
        return new ExternalLoginResult(Outcome.EMAIL_VERIFICATION_REQUIRED, null, null, verificationProof);
    }

    @Override
    public String toString() {
        return "ExternalLoginResult[outcome=" + outcome + ", session=" + session
                + ", challengeId=***, token=***]";
    }
}
