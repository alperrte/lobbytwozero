package com.ltz.game.infrastructure.client;

import com.ltz.game.domain.exception.ProviderException;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import tools.jackson.databind.JsonNode;

/** Fiyat yanıtının skaler güven sınırı; ham sağlayıcı verisi hata mesajına taşınmaz. */
final class ItadPriceValues {
    private ItadPriceValues() { }
    static BigDecimal amount(JsonNode row) {
        JsonNode node=row.get("amount");
        if(node==null || !node.isNumber()) throw invalid();
        var value=node.decimalValue();
        if(value.signum()<0 || value.precision()>19 || value.precision()-value.scale()>15 || value.scale()>4) throw invalid();
        return value;
    }
    static String text(JsonNode node,String key) {
        if(node==null || !node.isObject()) throw invalid();
        JsonNode field=node.get(key);
        if(field==null || !field.isTextual() || field.asText().isBlank() || field.asText().length()>2048) throw invalid();
        return field.asText();
    }
    static int percent(JsonNode row) {
        JsonNode cut=row.get("cut");
        if(cut==null || !cut.isIntegralNumber() || !cut.canConvertToInt() || cut.intValue()<0 || cut.intValue()>100) throw invalid();
        return cut.intValue();
    }
    static UUID uuid(String value) {
        try { UUID id=UUID.fromString(value);if(!id.toString().equals(value)) throw invalid();return id; }
        catch(IllegalArgumentException _) { throw invalid(); }
    }
    static Instant instant(String value) {
        try { return java.time.OffsetDateTime.parse(value).toInstant(); }
        catch(java.time.DateTimeException _) { throw invalid(); }
    }
    static String link(String value) {
        try {
            var uri=URI.create(value);
            if(!"https".equals(uri.getScheme()) || uri.getHost()==null || uri.getUserInfo()!=null
                    || !uri.getHost().equals("isthereanydeal.com") && !uri.getHost().endsWith(".isthereanydeal.com")) throw invalid();
            return value;
        } catch(IllegalArgumentException _) { throw invalid(); }
    }
    static Instant expires(JsonNode row,Instant now,Duration ttl) {
        Instant expires=now.plus(ttl);
        JsonNode expiry=row.get("expiry");
        if(expiry!=null && !expiry.isNull()) {
            Instant end=instant(expiry.asText());if(end.isBefore(expires)) expires=end;
        }
        if(!expires.isAfter(now)) throw new ProviderException("PRICE_STALE_SOURCE");
        return expires;
    }
    static ProviderException invalid() { return new ProviderException("INVALID_PRICE_RESPONSE"); }
}
