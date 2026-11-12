package com.ltz.messaging.application.port;

/**
 * GIF platformuna ulaşılamadı ya da anahtar yapılandırılmadı. Neden mesaja yazılmaz ve kök hata bağlanmaz: platform
 * adresi sorgu dizgesinde API anahtarını taşır, kök hata iletisi loglanırsa anahtar sızabilirdi.
 */
public class GifProviderUnavailableException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public GifProviderUnavailableException(String reason) {
        super(reason, null, false, false);
    }
}
