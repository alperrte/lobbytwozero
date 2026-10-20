package com.ltz.game.domain.exception;

/** URL/anahtar/ham yanıt içermeyen, raporlanabilir sağlayıcı hata kodu. */
public class ProviderException extends RuntimeException {
    private final String code;
    public ProviderException(String code) {
        super(code);
        this.code = code;
    }
    public String code() { return code; }
}
