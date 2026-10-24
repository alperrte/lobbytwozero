package com.ltz.game.domain.exception;
public class GameConflictException extends RuntimeException {
    private final String code;
    public GameConflictException(String code) { super(code); this.code = code; }
    public String code() { return code; }
}
