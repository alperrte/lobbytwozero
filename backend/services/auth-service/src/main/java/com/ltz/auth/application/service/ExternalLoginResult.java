package com.ltz.auth.application.service;

/** Harici girişin sonucu: oturum açıldı, 2FA bekleniyor veya (yalnız Steam) kayıt tamamlanmalı. */
public record ExternalLoginResult(Outcome outcome, IssuedSession session, String challengeId,
                                  String registrationToken) {

    public enum Outcome {
        AUTHENTICATED,
        MFA_REQUIRED,
        REGISTRATION_REQUIRED
    }

    static ExternalLoginResult from(LoginResult login) {
        return login.requiresSecondFactor()
                ? new ExternalLoginResult(Outcome.MFA_REQUIRED, null, login.challengeId(), null)
                : new ExternalLoginResult(Outcome.AUTHENTICATED, login.session(), null, null);
    }

    static ExternalLoginResult registrationRequired(String registrationToken) {
        return new ExternalLoginResult(Outcome.REGISTRATION_REQUIRED, null, null, registrationToken);
    }

    @Override
    public String toString() {
        return "ExternalLoginResult[outcome=" + outcome + ", session=" + session
                + ", challengeId=***, registrationToken=***]";
    }
}
