package com.ltz.user.application.port;

/** Depo okunamadı/yazılamadı/silinemedi. Mesaj dosya yolu ya da kullanıcı verisi taşımaz. */
public class MediaStorageException extends RuntimeException {

    public MediaStorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
