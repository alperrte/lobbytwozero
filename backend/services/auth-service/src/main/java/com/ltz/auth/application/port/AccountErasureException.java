package com.ltz.auth.application.port;

/** Hedef servis silmeyi onaylayamadı (erişilemez, yetkisiz, hata). Yalnız kısa hata kodu saklanır; ayrıntı taşınmaz. */
public class AccountErasureException extends RuntimeException {

    private final String code;

    public AccountErasureException(String code) {
        super(code);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
