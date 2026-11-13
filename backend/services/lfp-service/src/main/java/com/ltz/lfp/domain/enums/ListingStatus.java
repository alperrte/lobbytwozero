package com.ltz.lfp.domain.enums;

/**
 * İlan durumu (LFP-14). Durum makinesi: {@code OPEN → CLOSED} (sahibi kapatır) ve {@code OPEN → EXPIRED} (süre dolar);
 * kapanmış ya da süresi dolmuş ilan bir daha açılmaz. Yalnız {@code OPEN} ilan aktif listelerde görünür.
 */
public enum ListingStatus {
    OPEN,
    CLOSED,
    EXPIRED
}
