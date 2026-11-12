package com.ltz.auth.api.dto.request;

import java.time.LocalDate;

import jakarta.validation.constraints.NotNull;

/** Yaş beyanı. Doğum tarihi yalnız hesaplamada kullanılır; saklanmaz, loglanmaz, yanıta ya da hataya yansımaz. */
public record AgeVerificationRequest(@NotNull LocalDate birthDate) {

    @Override
    public String toString() {
        return "AgeVerificationRequest[birthDate=***]";
    }
}
