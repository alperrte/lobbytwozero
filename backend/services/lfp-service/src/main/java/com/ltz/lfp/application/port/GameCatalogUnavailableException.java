package com.ltz.lfp.application.port;

/** Oyunun katalogdaki durumu öğrenilemedi (game-service ulaşılamaz, hata döndü ya da yanıt tutarsız). */
public class GameCatalogUnavailableException extends RuntimeException {

    public GameCatalogUnavailableException(String message) {
        super(message);
    }

    public GameCatalogUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
