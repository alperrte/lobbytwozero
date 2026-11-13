package com.ltz.lfp.application.service;

import java.time.Instant;
import java.util.UUID;

import com.ltz.lfp.domain.entity.ListingText;
import com.ltz.lfp.domain.enums.Platform;
import com.ltz.lfp.domain.exception.LfpErrorCode;
import com.ltz.lfp.domain.exception.LfpException;

/**
 * LFP-11 arama süzgeçleri; hepsi isteğe bağlıdır ve birlikte (VE) uygulanır. Metin süzgeçleri büyük/küçük harfe
 * duyarsızdır.
 *
 * @param skillLevel seviye metninde geçen ifade; seviyesi <b>belirtilmemiş</b> ilanlar da eşleşir (seviye beklentisi
 *                   olmayan ilan her seviyeye açıktır)
 * @param role       ilanın rollerinden biriyle tam eşleşme
 * @param playFrom   planlanan başlangıç bu andan önce olmayan ilanlar (oyun zamanı belirtilmemiş ilan eşleşmez)
 * @param playTo     planlanan başlangıç bu andan sonra olmayan ilanlar
 * @param query      oyun adında ya da açıklamada geçen ifade
 */
public record ListingSearch(UUID gameId, Platform platform, String skillLevel, String role, Instant playFrom,
                            Instant playTo, Boolean micRequired, String query) {

    static final int MAX_QUERY = 100;

    /** Boş metin süzgeçlerini kaldırır, uzunluk ve aralık kurallarını uygular; ihlal 400 {@code INVALID_FILTER}. */
    ListingSearch normalized() {
        String skill = text(skillLevel, ListingText.MAX_SKILL_LEVEL);
        String roleKey = text(role, ListingText.MAX_ROLE);
        String text = text(query, MAX_QUERY);
        if (playFrom != null && playTo != null && playFrom.isAfter(playTo)) {
            throw new LfpException(LfpErrorCode.INVALID_FILTER);
        }
        return new ListingSearch(gameId, platform, skill, roleKey == null ? null : ListingText.roleKey(roleKey),
                playFrom, playTo, micRequired, text);
    }

    private static String text(String raw, int max) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String value = raw.strip();
        if (value.codePointCount(0, value.length()) > max
                || value.codePoints().anyMatch(Character::isISOControl)) {
            throw new LfpException(LfpErrorCode.INVALID_FILTER);
        }
        return value;
    }
}
