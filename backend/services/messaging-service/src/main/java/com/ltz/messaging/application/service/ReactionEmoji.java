package com.ltz.messaging.application.service;

import java.text.BreakIterator;
import java.util.Locale;

import com.ltz.messaging.domain.exception.MessagingErrorCode;
import com.ltz.messaging.domain.exception.MessagingException;

/**
 * Tepki emojisi doğrulaması (MSG-23): tek bir grafem kümesi (ZWJ dizileri, ten rengi, bayraklar ve keycap dahil) ve en
 * az bir emoji karakteri. Harf, boşluk, kontrol karakteri, eşlenmemiş UTF-16 vekil ve birden çok grafem reddedilir.
 * Uzunluk UTF-16 birimiyle en çok {@value #MAX_LENGTH} (şemadaki sınır).
 */
public final class ReactionEmoji {

    /** Tepki emojisinin azami uzunluğu (UTF-16 birimi). */
    public static final int MAX_LENGTH = 32;

    private static final int COMBINING_KEYCAP = 0x20E3;
    private static final int REGIONAL_INDICATOR_FIRST = 0x1F1E6;
    private static final int REGIONAL_INDICATOR_LAST = 0x1F1FF;

    private ReactionEmoji() {
    }

    /** Geçerliyse aynı değeri döner; değilse {@code 400 INVALID_REACTION}. */
    public static String validate(String raw) {
        if (raw == null || raw.isEmpty() || raw.length() > MAX_LENGTH || !isSingleGrapheme(raw)
                || !Shape.of(raw).isEmoji()) {
            throw new MessagingException(MessagingErrorCode.INVALID_REACTION);
        }
        return raw;
    }

    private static boolean isSingleGrapheme(String raw) {
        BreakIterator graphemes = BreakIterator.getCharacterInstance(Locale.ROOT);
        graphemes.setText(raw);
        return graphemes.next() == raw.length();
    }

    /** Grafemin kod noktası özellikleri. */
    private record Shape(int codePoints, boolean forbidden, boolean pictographic, boolean digit, boolean keycap,
                         int regionalIndicators) {

        static Shape of(String raw) {
            int count = 0;
            int regional = 0;
            boolean forbidden = false;
            boolean pictographic = false;
            boolean digit = false;
            boolean keycap = false;
            for (int codePoint : raw.codePoints().toArray()) {
                count++;
                forbidden |= isForbidden(codePoint);
                pictographic |= Character.isExtendedPictographic(codePoint);
                digit |= Character.isDigit(codePoint);
                keycap |= codePoint == COMBINING_KEYCAP;
                regional += isRegionalIndicator(codePoint) ? 1 : 0;
            }
            return new Shape(count, forbidden, pictographic, digit, keycap, regional);
        }

        /** Bayrak (iki bölgesel gösterge), keycap ya da bölgesel gösterge ve rakam içermeyen resimli emoji. */
        boolean isEmoji() {
            if (forbidden) {
                return false;
            }
            boolean flag = regionalIndicators == 2 && codePoints == 2;
            return flag || keycap || pictographic && regionalIndicators == 0 && !digit;
        }

        private static boolean isForbidden(int codePoint) {
            return Character.getType(codePoint) == Character.SURROGATE || Character.isWhitespace(codePoint)
                    || Character.isISOControl(codePoint) || Character.isLetter(codePoint);
        }

        private static boolean isRegionalIndicator(int codePoint) {
            return codePoint >= REGIONAL_INDICATOR_FIRST && codePoint <= REGIONAL_INDICATOR_LAST;
        }
    }
}
