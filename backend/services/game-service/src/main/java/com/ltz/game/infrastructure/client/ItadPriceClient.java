package com.ltz.game.infrastructure.client;

import com.ltz.game.application.port.GamePriceProvider;
import com.ltz.game.domain.exception.ProviderException;
import com.ltz.game.infrastructure.config.GamePriceProperties;
import com.ltz.game.infrastructure.persistence.repository.GameExternalReferenceRepository;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Resmî ITAD toplu eşleme + prices/v3. İsim eşlemesi, kur dönüşümü, affiliate URL değişikliği yoktur. */
public class ItadPriceClient implements GamePriceProvider {
    private static final JsonMapper JSON=JsonMapper.builder().build();
    private final RestClient http;
    private final GamePriceProperties properties;
    private final GameExternalReferenceRepository references;
    private final Clock clock;

    public ItadPriceClient(RestClient http,GamePriceProperties properties,GameExternalReferenceRepository references,Clock clock) {
        this.http=http;this.properties=properties;this.references=references;this.clock=clock;
    }
    @Override public boolean isConfigured() { return properties.apiKey()!=null && !properties.apiKey().isBlank(); }

    @Override public List<Quote> prices(Set<UUID> ids,Market market) {
        if(ids.isEmpty()) return List.of();
        if(ids.size()>200) throw new IllegalArgumentException("En fazla 200 oyun.");
        if(!isConfigured()) throw new ProviderException("PRICE_NOT_CONFIGURED");
        Map<String,UUID> steam=new LinkedHashMap<>();
        references.findByGameIdIn(ids).stream().filter(ref -> "STEAM".equals(ref.getProvider()))
                .forEach(ref -> steam.put("app/"+ref.getExternalId(),ref.getGame().getId()));
        if(steam.isEmpty()) return List.of();
        if(steam.size()>200) throw new ProviderException("PRICE_MAPPING_LIMIT");
        var matched=mapGames(post("/lookup/id/shop/61/v1",new ArrayList<>(steam.keySet())),steam);
        if(matched.isEmpty()) return List.of();
        var rows=post("/games/prices/v3?country="+market.country()+"&capacity=5&vouchers=false",new ArrayList<>(matched.keySet()));
        return ItadPriceMapper.quotes(rows,matched,market,clock.instant(),properties.coldTtl());
    }
    private static Map<UUID,List<UUID>> mapGames(JsonNode data,Map<String,UUID> steam) {
        if(data==null || !data.isObject()) throw ItadPriceValues.invalid();
        Map<UUID,List<UUID>> matched=new LinkedHashMap<>();
        Map<UUID,UUID> canonical=new java.util.HashMap<>();
        for(var entry:steam.entrySet()) {
            JsonNode node=data.get(entry.getKey());
            if(node==null) throw ItadPriceValues.invalid();
            if(node.isNull()) continue;
            if(!node.isTextual()) throw ItadPriceValues.invalid();
            UUID external=ItadPriceValues.uuid(node.asText());
            UUID previous=canonical.putIfAbsent(entry.getValue(),external);
            if(previous!=null && !previous.equals(external)) throw new ProviderException("AMBIGUOUS_PRICE_MAPPING");
            var targets=matched.computeIfAbsent(external,key -> new ArrayList<>());
            if(!targets.contains(entry.getValue())) targets.add(entry.getValue());
        }
        return matched;
    }
    private JsonNode post(String path,Object body) {
        try {
            return http.post().uri(path).header("ITAD-API-Key",properties.apiKey())
                    .header("User-Agent","LTZ-Game-Service/1.0").contentType(MediaType.APPLICATION_JSON)
                    .body(body).exchange((request,response) -> {
                        int status=response.getStatusCode().value();
                        if(status!=200) throw failure(status,response.getHeaders().getFirst("Retry-After"));
                        byte[] bytes=response.getBody().readNBytes(4_194_305);
                        if(bytes.length>4_194_304) throw ItadPriceValues.invalid();
                        try { return JSON.readTree(bytes); } catch(RuntimeException _) { throw ItadPriceValues.invalid(); }
                    });
        } catch(RestClientException _) { throw new ProviderException("PRICE_UNAVAILABLE"); }
    }
    private ProviderException failure(int status,String after) {
        if(status==429) return new ProviderException("PRICE_RATE_LIMITED",retryAfter(after));
        return new ProviderException(status==401 || status==403?"PRICE_ACCESS_DENIED":"PRICE_UNAVAILABLE");
    }
    private Duration retryAfter(String value) {
        if(value==null) return Duration.ofMinutes(15);
        if(value.matches("\\d{1,9}")) return Duration.ofSeconds(Math.max(1,Long.parseLong(value)));
        try {
            var end=java.time.ZonedDateTime.parse(value,java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME).toInstant();
            return Duration.ofSeconds(Math.max(1,Duration.between(clock.instant(),end).getSeconds()));
        } catch(java.time.DateTimeException _) {
            return Duration.ofMinutes(15);
        }
    }
}
