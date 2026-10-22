package com.ltz.game.domain.exception;

/** URL/anahtar/ham yanıt içermeyen, raporlanabilir sağlayıcı hata kodu. */
public class ProviderException extends RuntimeException {
    private final String code;
    private final java.time.Duration retryAfter;
    public ProviderException(String code) {
        this(code, null);
    }
    public ProviderException(String code, java.time.Duration retryAfter) {
        super(safeCode(code));
        this.code = safeCode(code);
        this.retryAfter = retryAfter;
    }
    private static String safeCode(String code) {
        return code != null && code.matches("[A-Z][A-Z0-9_]{0,63}") ? code : "PROVIDER_ERROR";
    }
    public String code() { return code; }
    public java.time.Duration retryAfter() { return retryAfter; }
}
