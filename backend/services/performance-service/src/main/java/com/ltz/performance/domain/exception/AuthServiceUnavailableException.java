package com.ltz.performance.domain.exception;

/** Auth doğrulaması yapılamadığında kapalı başarısızlık: bilinmeyen durum asla "izinli" sayılmaz. */
public class AuthServiceUnavailableException extends RuntimeException {
    public AuthServiceUnavailableException() { super("Auth service unavailable"); }
}
