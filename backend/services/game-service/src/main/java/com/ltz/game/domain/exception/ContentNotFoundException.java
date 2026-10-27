package com.ltz.game.domain.exception;
/** Oyun dışındaki bir kaynak (içerik, yetki kaydı, manuel kilit) bulunamadı. */
public class ContentNotFoundException extends RuntimeException {
    private final String code;
    public ContentNotFoundException(String code) { super(code); this.code = code; }
    public String code() { return code; }
}
