package com.ltz.game.domain.exception;
public class InvalidGameRequestException extends RuntimeException {
    private final String code;
    public InvalidGameRequestException(String code) { super(code); this.code = code; }
    public String code() { return code; }
}
