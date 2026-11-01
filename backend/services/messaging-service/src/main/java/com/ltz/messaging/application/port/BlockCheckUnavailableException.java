package com.ltz.messaging.application.port;

/** Engel durumu öğrenilemedi (gizli anahtar yok, Auth/Social reddetti ya da ulaşılamadı). İletide token/anahtar yoktur. */
public class BlockCheckUnavailableException extends RuntimeException {

    public BlockCheckUnavailableException(String message) {
        super(message);
    }

    public BlockCheckUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
