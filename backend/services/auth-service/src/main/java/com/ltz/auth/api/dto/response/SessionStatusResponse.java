package com.ltz.auth.api.dto.response;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Giriş/yenileme yanıtı. Token'lar gövdede dönmez (yalnız HttpOnly cookie). MFA_REQUIRED durumunda
 * yalnız challenge bilgisi döner ve hiçbir oturum cookie'si yazılmaz.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SessionStatusResponse(Status status, Instant accessTokenExpiresAt,
                                    String challengeId, Instant challengeExpiresAt) {

    public enum Status {
        AUTHENTICATED,
        MFA_REQUIRED
    }

    public static SessionStatusResponse authenticated(Instant accessTokenExpiresAt) {
        return new SessionStatusResponse(Status.AUTHENTICATED, accessTokenExpiresAt, null, null);
    }

    public static SessionStatusResponse mfaRequired(String challengeId, Instant challengeExpiresAt) {
        return new SessionStatusResponse(Status.MFA_REQUIRED, null, challengeId, challengeExpiresAt);
    }

    @Override
    public String toString() {
        return "SessionStatusResponse[status=" + status + ", accessTokenExpiresAt=" + accessTokenExpiresAt
                + ", challengeId=***, challengeExpiresAt=" + challengeExpiresAt + "]";
    }
}
