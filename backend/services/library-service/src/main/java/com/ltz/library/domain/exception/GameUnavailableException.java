package com.ltz.library.domain.exception;

public class GameUnavailableException extends RuntimeException {
    public GameUnavailableException() { super("Oyun bulunamadı veya koleksiyona eklenemiyor."); }
}
