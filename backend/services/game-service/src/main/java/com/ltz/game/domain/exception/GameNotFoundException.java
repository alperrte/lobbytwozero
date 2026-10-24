package com.ltz.game.domain.exception;
public class GameNotFoundException extends RuntimeException {
    public GameNotFoundException() { super("Oyun bulunamadı."); }
}
