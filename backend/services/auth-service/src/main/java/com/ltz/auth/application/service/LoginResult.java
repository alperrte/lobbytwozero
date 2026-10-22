package com.ltz.auth.application.service;

import java.time.Instant;

/** Giriş sonucu: ya oturum açılır ya da ikinci faktör beklenir (bu durumda token verilmez). */
public record LoginResult(IssuedSession session, String challengeId, Instant challengeExpiresAt) {

    static LoginResult authenticated(IssuedSession session) {
        return new LoginResult(session, null, null);
    }

    static LoginResult mfaRequired(String challengeId, Instant challengeExpiresAt) {
        return new LoginResult(null, challengeId, challengeExpiresAt);
    }

    public boolean requiresSecondFactor() {
        return session == null;
    }

    @Override
    public String toString() {
        return "LoginResult[session=" + session + ", challengeId=***, challengeExpiresAt=" + challengeExpiresAt + "]";
    }
}
