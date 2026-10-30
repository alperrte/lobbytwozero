package com.ltz.user.application.service;

import java.util.Map;
import java.util.UUID;

import com.ltz.user.domain.exception.UserErrorCode;
import com.ltz.user.domain.exception.UserException;

/** Olay yükü alanlarının katı okunması; eksik ya da yanlış tipli alan {@code INVALID_EVENT} olur (değer yansıtılmaz). */
final class EventPayload {

    /** Sayaçlar için mantıklı üst sınır; bunun üstü bozuk veri sayılır. */
    private static final long MAX_COUNT = 10_000_000L;

    private EventPayload() {
    }

    static String text(Map<String, Object> payload, String field) {
        if (payload.get(field) instanceof String value && !value.isBlank()) {
            return value;
        }
        throw invalid();
    }

    static UUID uuid(Map<String, Object> payload, String field) {
        try {
            return UUID.fromString(text(payload, field));
        } catch (IllegalArgumentException _) {
            throw invalid();
        }
    }

    /** Negatif olmayan tam sayı (JSON sayısı; ondalık ya da metin kabul edilmez). */
    static int count(Map<String, Object> payload, String field) {
        Object value = payload.get(field);
        if ((value instanceof Integer || value instanceof Long) && ((Number) value).longValue() >= 0
                && ((Number) value).longValue() <= MAX_COUNT) {
            return ((Number) value).intValue();
        }
        throw invalid();
    }

    static UserException invalid() {
        return new UserException(UserErrorCode.INVALID_EVENT);
    }
}
