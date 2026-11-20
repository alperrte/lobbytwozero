package com.ltz.recommendation.infrastructure.client;

import com.ltz.recommendation.domain.exception.RecommendationRequestException;
import java.time.Instant;
import java.util.UUID;
import tools.jackson.databind.JsonNode;

/** Yalnız assessment kaynak yanıtlarının sınırlı primitive şema okuyucuları. */
final class AssessmentValues {
    private AssessmentValues() { }

    static Double fps(JsonNode item) {
        if (item.isNull() || item.isMissingNode()) return null;
        if (!item.isNumber() || !Double.isFinite(item.asDouble()) || item.asDouble() < 0) throw invalid();
        return item.asDouble();
    }

    static long count(JsonNode item) {
        if (!item.isIntegralNumber() || !item.canConvertToLong() || item.asLong() < 0) throw invalid();
        return item.asLong();
    }

    static Integer integer(JsonNode item, int max) {
        if (item.isNull() || item.isMissingNode()) return null;
        if (!item.isIntegralNumber() || !item.canConvertToInt() || item.asInt() < 1 || item.asInt() > max) throw invalid();
        return item.asInt();
    }

    static String text(JsonNode item, int max) {
        if (item.isNull() || item.isMissingNode()) return null;
        if (!item.isString() || item.asString().length() > max) throw invalid();
        return item.asString().isBlank() ? null : item.asString().strip();
    }

    static UUID uuid(JsonNode item) {
        if (!item.isString()) throw invalid();
        try {
            UUID value = UUID.fromString(item.asString());
            if (!value.toString().equalsIgnoreCase(item.asString())) throw invalid();
            return value;
        } catch (IllegalArgumentException _) { throw invalid(); }
    }

    static Instant instant(JsonNode item) {
        if (item.isNull() || item.isMissingNode()) return null;
        if (!item.isString()) throw invalid();
        try { return Instant.parse(item.asString()); }
        catch (java.time.DateTimeException _) { throw invalid(); }
    }
    static RecommendationRequestException invalid() { return new RecommendationRequestException(503, "INVALID_SOURCE_RESPONSE"); }
}
