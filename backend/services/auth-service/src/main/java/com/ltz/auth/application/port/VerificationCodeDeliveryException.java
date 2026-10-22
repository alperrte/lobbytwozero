package com.ltz.auth.application.port;

public class VerificationCodeDeliveryException extends RuntimeException {

    public VerificationCodeDeliveryException(String message) {
        super(message);
    }

    public VerificationCodeDeliveryException(String message, Throwable cause) {
        super(message, cause);
    }
}
