package com.ltz.auth.application.port;

/** Sağlayıcı doğrulaması başarısız (imza, nonce, aud, erişilemezlik vb.). Ayrıntı istemciye verilmez. */
public class ExternalAuthenticationException extends RuntimeException {

    public ExternalAuthenticationException(String message) {
        super(message);
    }

    public ExternalAuthenticationException(String message, Throwable cause) {
        super(message, cause);
    }
}
