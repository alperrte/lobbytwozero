package com.ltz.auth.domain.exception;

/** Eşzamanlı parola hash'i sınırı dolduğunda istek reddedilir; bellek tükenmesi önlenir. */
public class PasswordHashingCapacityExceededException extends RuntimeException {

    public PasswordHashingCapacityExceededException() {
        super("Password hashing capacity exceeded");
    }
}
