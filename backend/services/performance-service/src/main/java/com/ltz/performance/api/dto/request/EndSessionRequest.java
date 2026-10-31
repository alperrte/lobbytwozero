package com.ltz.performance.api.dto.request;

import com.ltz.performance.domain.enums.SessionStatus;

/** Gövde isteğe bağlıdır; verilmezse oturum COMPLETED olarak biter. ACTIVE geçerli bir bitiş durumu değildir. */
public record EndSessionRequest(SessionStatus status) {
    public SessionStatus targetOrDefault() { return status == null ? SessionStatus.COMPLETED : status; }
}
