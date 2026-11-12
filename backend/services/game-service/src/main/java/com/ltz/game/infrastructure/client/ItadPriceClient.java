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

/**
 * Resmî ITAD toplu eşleme + prices/v3. İsim eşlemesi, kur dönüşümü, affiliate URL değişikliği yoktur. Her oyun için ITAD'in döndürdüğü TÜM
 * mağaza teklifleri alınır (mağaza sayısı sınırlanmaz). İstekler istemci tarafı hız sınırlayıcıdan geçer ({@link ItadRequestLimiter}).
 */
public class ItadPriceClient implements GamePriceProvider {
    private static final JsonMapper JSON=JsonMapper.builder().build();
    /** Yanıt gövdesi üst sınırı: 200 oyun x ≈64 teklif için pay (gerçek ölçüm: tüm mağazalarla ≈0,2 MB). */
    private static final int MAX_BODY=16_777_216;
    private final RestClient http;
    private final GamePriceProperties properties;
    private final GameExternalReferenceRepository references;
    private final Clock clock;
    private final ItadRequestLimiter limiter;
    private final ItadTelemetry telemetry;
    /** ITAD 429 ile Retry-After bildirdiğinde tüm iş parçacıkları o zamana kadar yeni istek atmaz (her biri ayrı 429 yemesin). */
    private volatile java.time.Instant blockedUntil=java.time.Instant.MIN;
    /** Steam AppID ("app/{id}") → ITAD oyun kimliği. Eşleme kalıcıdır; bir kez öğrenilince tekrar sorulmaz (her turdaki ikinci ITAD isteği ve gecikmesi kalkar). Yalnız olumlu eşleşmeler tutulur. */
    private final Map<String,UUID> knownMappings=new java.util.concurrent.ConcurrentHashMap<>();
    /** Bellek üst sınırı (yaklaşık 40 MB): dolarsa temizlenir ve yeniden öğrenilir. */
    static final int MAX_KNOWN_MAPPINGS=400_000;

    public ItadPriceClient(RestClient http,GamePriceProperties properties,GameExternalReferenceRepository references,Clock clock) {
        this(http,properties,references,clock,new ItadRequestLimiter(clock));
    }
    ItadPriceClient(RestClient http,GamePriceProperties properties,GameExternalReferenceRepository references,Clock clock,ItadRequestLimiter limiter) {
        this.http=http;this.properties=properties;this.references=references;this.clock=clock;this.limiter=limiter;this.telemetry=new ItadTelemetry(clock);
    }
    /** Yalnız testler için: öğrenilmiş eşlemeleri unutur. */
    void forgetMappingsForTests() { knownMappings.clear(); }
    @Override public boolean isConfigured() { return properties.apiKey()!=null && !properties.apiKey().isBlank(); }

    @Override public List<Quote> prices(Set<UUID> ids,Market market) { return prices(ids,market,Priority.BACKGROUND); }

    @Override public List<Quote> prices(Set<UUID> ids,Market market,Priority priority) {
        if(ids.isEmpty()) return List.of();
        if(ids.size()>200) throw new IllegalArgumentException("En fazla 200 oyun.");
        if(!isConfigured()) throw new ProviderException("PRICE_NOT_CONFIGURED");
        Map<String,UUID> steam=new LinkedHashMap<>();
        references.findByGameIdIn(ids).stream().filter(ref -> "STEAM".equals(ref.getProvider()))
                .forEach(ref -> steam.put("app/"+ref.getExternalId(),ref.getGame().getId()));
        if(steam.isEmpty()) return List.of();
        if(steam.size()>200) throw new ProviderException("PRICE_MAPPING_LIMIT");
        var call=new ItadTelemetry.Call("prices",market.country()+"-"+market.currency(),priority==Priority.INTERACTIVE);
        resolveMappings(steam,call);
        var matched=group(steam);
        if(matched.isEmpty()) return List.of();
        var rows=post("/games/prices/v3?country="+market.country()+"&vouchers=false",new ArrayList<>(matched.keySet()),call);
        return ItadPriceMapper.quotes(rows,matched,market,clock.instant(),properties.displayTtl(properties.coldTtl()));
    }
    /** Henüz bilinmeyen AppID'leri ITAD'e sorar ve olumlu eşleşmeleri önbelleğe alır; bilinenler için istek atılmaz. */
    private void resolveMappings(Map<String,UUID> steam,ItadTelemetry.Call prices) {
        List<String> unknown=steam.keySet().stream().filter(key -> !knownMappings.containsKey(key)).toList();
        if(unknown.isEmpty()) return;
        JsonNode data=post("/lookup/id/shop/61/v1",new ArrayList<>(unknown),new ItadTelemetry.Call("lookup",prices.market(),prices.interactive()));
        if(data==null || !data.isObject()) throw ItadPriceValues.invalid();
        if(knownMappings.size()+unknown.size()>MAX_KNOWN_MAPPINGS) knownMappings.clear();
        Map<String,UUID> learned=new java.util.HashMap<>();
        for(String key:unknown) {
            JsonNode node=data.get(key);
            if(node==null) throw ItadPriceValues.invalid();
            if(node.isNull()) continue;
            if(!node.isTextual()) throw ItadPriceValues.invalid();
            learned.put(key,ItadPriceValues.uuid(node.asText()));
        }
        knownMappings.putAll(learned);
    }
    /** ITAD oyun kimliği → LTZ oyunları; bir LTZ oyunu iki farklı ITAD oyununa eşlenirse belirsiz eşleme hatasıdır. */
    private Map<UUID,List<UUID>> group(Map<String,UUID> steam) {
        Map<UUID,List<UUID>> matched=new LinkedHashMap<>();
        Map<UUID,UUID> canonical=new java.util.HashMap<>();
        for(var entry:steam.entrySet()) {
            UUID external=knownMappings.get(entry.getKey());
            if(external==null) continue;
            UUID previous=canonical.putIfAbsent(entry.getValue(),external);
            if(previous!=null && !previous.equals(external)) throw new ProviderException("AMBIGUOUS_PRICE_MAPPING");
            var targets=matched.computeIfAbsent(external,key -> new ArrayList<>());
            if(!targets.contains(entry.getValue())) targets.add(entry.getValue());
        }
        return matched;
    }
    private JsonNode post(String path,Object body,ItadTelemetry.Call call) {
        java.time.Instant blocked=blockedUntil;
        if(blocked.isAfter(clock.instant())) throw new ProviderException(ItadRequestLimiter.LOCAL_THROTTLE,Duration.between(clock.instant(),blocked));
        limiter.acquire(call.interactive());
        int[] status={0};
        long started=System.nanoTime();
        telemetry.start();
        try {
            return http.post().uri(path).header("ITAD-API-Key",properties.apiKey())
                    .header("User-Agent","LTZ-Game-Service/1.0").contentType(MediaType.APPLICATION_JSON)
                    .body(body).exchange((request,response) -> {
                        status[0]=response.getStatusCode().value();
                        if(status[0]!=200) throw failure(status[0],response.getHeaders().getFirst("Retry-After"),call);
                        telemetry.observeHeaders(response.getHeaders());
                        byte[] bytes=response.getBody().readNBytes(MAX_BODY+1);
                        if(bytes.length>MAX_BODY) throw ItadPriceValues.invalid();
                        try { return JSON.readTree(bytes); } catch(RuntimeException _) { throw ItadPriceValues.invalid(); }
                    });
        } catch(RestClientException _) { throw new ProviderException("PRICE_UNAVAILABLE"); }
        finally { telemetry.finish(call,status[0],(System.nanoTime()-started)/1_000_000); }
    }
    private ProviderException failure(int status,String after,ItadTelemetry.Call call) {
        if(status==429) {
            Duration wait=retryAfter(after);
            Duration block=wait.compareTo(Duration.ofMinutes(10))>0 ? Duration.ofMinutes(10) : wait;
            blockedUntil=clock.instant().plus(block);
            telemetry.rateLimited(call,limiter.snapshot(),after,block);
            return new ProviderException("PRICE_RATE_LIMITED",wait);
        }
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
