package com.ltz.auth.domain.exception;

/** Kullanıcı adları herkese açık olduğundan çakışma 409 ile bildirilir (K1). */
public class UsernameAlreadyTakenException extends RuntimeException {

    public UsernameAlreadyTakenException() {
        super("Username is already taken");
    }

    /** Eşzamanlı çakışmada veritabanı nedeni yalnız {@link RedactedCause} ile (kayıt değeri taşımadan) eklenir. */
    public UsernameAlreadyTakenException(Throwable cause) {
        super("Username is already taken", cause);
    }
}
