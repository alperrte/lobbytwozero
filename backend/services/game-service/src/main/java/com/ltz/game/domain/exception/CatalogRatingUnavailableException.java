package com.ltz.game.domain.exception;
public class CatalogRatingUnavailableException extends IllegalArgumentException {
    public CatalogRatingUnavailableException() { super("LTZ puanı entegrasyonu bekleniyor."); }
}
