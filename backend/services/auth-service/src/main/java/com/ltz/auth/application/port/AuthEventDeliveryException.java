package com.ltz.auth.application.port;

/** Olay hedefe teslim edilemedi (erişilemez, yetkisiz, hata). Yalnız kısa hata kodu saklanır; ayrıntı taşınmaz. */
public class AuthEventDeliveryException extends RuntimeException {

    private final String code;

    public AuthEventDeliveryException(String code) {
        super(code);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
