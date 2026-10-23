package com.ltz.game.domain.exception;
public class ReferenceMappingConflictException extends RuntimeException {
    public ReferenceMappingConflictException() { super("Referansın oyun eşlemesi değişmiş; yeniden doğrulama gerekiyor."); }
}
