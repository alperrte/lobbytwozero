package com.ltz.library.domain.exception;

/** Özet olayı teslim edilemedi; relay işareti geri çekilmeyle yeniden dener. Mesaj yalnız güvenli neden içerir. */
public class SummaryDeliveryException extends RuntimeException {
    public SummaryDeliveryException(String message) { super(message); }

    public SummaryDeliveryException(String message, Throwable cause) { super(message, cause); }
}
