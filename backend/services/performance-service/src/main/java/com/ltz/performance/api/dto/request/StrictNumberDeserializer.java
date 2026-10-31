package com.ltz.performance.api.dto.request;

import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;

/**
 * Yalnız JSON sayısını kabul eder. Jackson'ın varsayılanı {@code "60"} gibi metni, {@code true} gibi mantıksal
 * değeri veya {@code ""} metnini sayıya çevirir; ölçüm değerinde bu sessiz dönüşüm istenmez (bozuk girdi 0 veya
 * başka bir sayıya dönüşmemeli). Diğer her belirteç 400 ile reddedilir; null doğrulama katmanında yakalanır.
 */
public class StrictNumberDeserializer extends ValueDeserializer<Double> {
    @Override
    public Double deserialize(JsonParser parser, DeserializationContext context) {
        JsonToken token = parser.currentToken();
        if (token == JsonToken.VALUE_NUMBER_INT || token == JsonToken.VALUE_NUMBER_FLOAT) {
            return parser.getDoubleValue();
        }
        return (Double) context.handleUnexpectedToken(Double.class, parser);
    }
}
