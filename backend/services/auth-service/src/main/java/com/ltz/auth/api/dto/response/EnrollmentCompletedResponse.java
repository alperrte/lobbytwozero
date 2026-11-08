package com.ltz.auth.api.dto.response;

import java.time.Instant;
import java.util.List;

/** Kurulum tamamlandı: oturum çerezlerle açılır (gövdede token yok); kurtarma kodları yalnız bu yanıtta görünür. */
public record EnrollmentCompletedResponse(SessionStatusResponse.Status status, Instant accessTokenExpiresAt,
                                          List<String> recoveryCodes) {

    public EnrollmentCompletedResponse {
        recoveryCodes = List.copyOf(recoveryCodes);
    }

    @Override
    public String toString() {
        return "EnrollmentCompletedResponse[status=" + status + ", accessTokenExpiresAt=" + accessTokenExpiresAt
                + ", recoveryCodes=***]";
    }
}
