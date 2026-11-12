package com.ltz.game.application.service;

import java.time.Duration;

/**
 * Sayfası açılan oyunun anlık fiyat yenilemesinin sonucu. {@code refreshed}: yenilenen oyun sayısı, yapılamadıysa {@code -1}.
 * {@code retryable}: neden geçici (kota, yerel kısıtlama, ITAD bloğu, kiralama başka yerde) ve ipucu sonra yeniden denenebilir; {@code retryAfter} en erken
 * ne zaman (bilinmiyorsa {@code null}). Kalıcı nedenler (yapılandırılmamış pazar, veri hatası) yeniden denenmez.
 */
public record RefreshNowResult(int refreshed, boolean retryable, Duration retryAfter) {
    /** Yenilenemedi ve yeniden denemenin anlamı yok. */
    public static final RefreshNowResult NOT_REFRESHED = new RefreshNowResult(-1, false, null);

    public static RefreshNowResult done(int count) { return new RefreshNowResult(count, false, null); }

    public static RefreshNowResult retry(Duration after) { return new RefreshNowResult(-1, true, after); }
}
