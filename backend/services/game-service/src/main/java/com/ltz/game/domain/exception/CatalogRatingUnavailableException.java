package com.ltz.game.domain.exception;
/** LTZ puan projeksiyonu henüz hiç hatasız taranmadığı için puana göre filtre/sıralama yapılamaz. */
public class CatalogRatingUnavailableException extends IllegalArgumentException {
    public CatalogRatingUnavailableException() { super("LTZ puan projeksiyonu henüz hazır değil."); }
}
