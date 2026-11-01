package com.ltz.messaging.application.port;

/** Auth'tan servis token'ı alınamadı (gizli anahtar yok, Auth reddetti ya da ulaşılamadı). İletide token/anahtar yoktur. */
public class ServiceTokenUnavailableException extends RuntimeException {

    public ServiceTokenUnavailableException(String message) {
        super(message);
    }

    public ServiceTokenUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
