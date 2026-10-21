package com.ltz.game.domain.exception;
/** Kimliği doğrulanmış aktörün bu kaynak üzerinde yetkisi yoktur. */
public class AccessForbiddenException extends RuntimeException {
    public AccessForbiddenException() { super("Bu işlem için yetkiniz yok."); }
}
