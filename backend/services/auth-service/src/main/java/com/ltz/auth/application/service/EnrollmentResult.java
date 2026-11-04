package com.ltz.auth.application.service;

import java.util.List;

/** Zorunlu 2FA kurulumunun sonucu: açılan oturum ve yalnız bu yanıtta görünen kurtarma kodları. */
public record EnrollmentResult(IssuedSession session, List<String> recoveryCodes) {

    public EnrollmentResult {
        recoveryCodes = List.copyOf(recoveryCodes);
    }

    @Override
    public String toString() {
        return "EnrollmentResult[session=" + session + ", recoveryCodes=***]";
    }
}
