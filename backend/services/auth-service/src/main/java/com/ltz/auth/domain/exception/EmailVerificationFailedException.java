package com.ltz.auth.domain.exception;

/**
 * Bilinmeyen e-posta, yanlış, süresi dolmuş, tüketilmiş veya deneme hakkı bitmiş kod için tek hata.
 * Nedeni ayırt etmemek hesap varlığının sızmasını engeller.
 */
public class EmailVerificationFailedException extends RuntimeException {

    public EmailVerificationFailedException() {
        super("Email verification failed");
    }
}
