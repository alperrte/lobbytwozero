package com.ltz.library.domain.exception;

public class CatalogUnavailableException extends RuntimeException {
    public CatalogUnavailableException() { super("Oyun doğrulama servisi geçici olarak kullanılamıyor."); }
    public CatalogUnavailableException(Throwable cause) {
        super("Oyun doğrulama servisi geçici olarak kullanılamıyor.", cause);
    }
}
