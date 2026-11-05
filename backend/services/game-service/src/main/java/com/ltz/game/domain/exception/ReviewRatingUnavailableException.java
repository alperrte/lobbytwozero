package com.ltz.game.domain.exception;

/**
 * Review'dan puan verisi alınamadı. {@code code} kalıcı durum kaydına yazılan, gizli değer taşımayan sabit koddur;
 * ileti yanıt gövdesi, token veya anahtar içermez.
 */
public class ReviewRatingUnavailableException extends RuntimeException {
    public static final String TOKEN_UNAVAILABLE = "TOKEN_UNAVAILABLE";
    public static final String REVIEW_REJECTED = "REVIEW_REJECTED";
    public static final String REVIEW_UNAVAILABLE = "REVIEW_UNAVAILABLE";
    public static final String INVALID_RESPONSE = "INVALID_RESPONSE";

    private final String code;

    public ReviewRatingUnavailableException(String code) {
        super("Review puan verisi alınamadı: " + code);
        this.code = code;
    }

    public ReviewRatingUnavailableException(String code, Throwable cause) {
        super("Review puan verisi alınamadı: " + code, cause);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
