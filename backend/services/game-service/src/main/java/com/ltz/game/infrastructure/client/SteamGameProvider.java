package com.ltz.game.infrastructure.client;

import com.ltz.game.application.port.GameProvider;
import com.ltz.game.domain.exception.ProviderException;
import com.ltz.game.infrastructure.client.dto.response.SteamCatalogEnvelope;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** Sadece belgelenmiş GetAppList; mağaza scraping veya belgelenmemiş detay çağrısı yoktur. */
public class SteamGameProvider implements GameProvider {
    private static final String ENDPOINT = "https://api.steampowered.com/IStoreService/GetAppList/v1/";
    private final RestClient client;
    private final String key;
    private final boolean enabled;
    private final SteamRequestBudget budget;

    public SteamGameProvider(RestClient client, String key, boolean enabled, SteamRequestBudget budget) {
        this.client = client;
        this.key = key;
        this.enabled = enabled;
        this.budget = budget;
    }
    @Override public String provider() { return "STEAM"; }
    @Override public Set<Capability> capabilities() { return Set.of(Capability.CATALOG, Capability.INCREMENTAL_CATALOG); }

    @Override public CatalogPage catalog(CatalogQuery query) {
        if (!enabled || key == null || key.isBlank()) throw new ProviderException("PROVIDER_NOT_CONFIGURED");
        budget.acquire();
        // JSON anahtar isimleri sabittir; yalnız doğrulanmış sayısal değerler yerleştirilir.
        String input = "{\"include_games\":true,\"include_dlc\":false,\"include_software\":false,"
                + "\"include_videos\":false,\"include_hardware\":false,\"last_appid\":" + query.afterAppId()
                + ",\"max_results\":" + query.size()
                + (query.modifiedSince() == null ? "" : ",\"if_modified_since\":" + query.modifiedSince().getEpochSecond()) + "}";
        try {
            SteamCatalogEnvelope envelope = client.get().uri(ENDPOINT + "?input_json={input}", input)
                    .header("x-webapi-key", key)
                    .exchange((request, response) -> {
                        validateStatus(response.getStatusCode().value(), response.getHeaders().getFirst("Retry-After"));
                        byte[] body = response.getBody().readNBytes(1048577);
                        if (body.length > 1048576) throw new ProviderException("INVALID_PROVIDER_RESPONSE");
                        try {
                            return tools.jackson.databind.json.JsonMapper.builder().build()
                                    .readValue(body, SteamCatalogEnvelope.class);
                        } catch (tools.jackson.core.JacksonException _) {
                            throw new ProviderException("INVALID_PROVIDER_RESPONSE");
                        }
                    });
            return normalize(query, envelope);
        } catch (RestClientException _) {
            // Ağ istisnasının ayrıntıları ve cause dışarı taşınmaz.
            throw new ProviderException("PROVIDER_TRANSPORT_ERROR");
        }
    }
    private void validateStatus(int status, String retryAfterHeader) {
        if (status == 401 || status == 403) throw new ProviderException("PROVIDER_ACCESS_DENIED");
        if (status == 429) throw new ProviderException("RATE_LIMITED", retryAfter(retryAfterHeader));
        if (status >= 500) throw new ProviderException("PROVIDER_UNAVAILABLE", retryAfter(retryAfterHeader));
        if (status != 200) throw new ProviderException("PROVIDER_REQUEST_REJECTED");
    }
    private java.time.Duration retryAfter(String value) {
        if (value == null) return null;
        try {
            if (value.matches("\\d+")) return retryAfterSeconds(value);
            var time = java.time.ZonedDateTime.parse(value, java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME).toInstant();
            var delay = java.time.Duration.between(Instant.now(), time);
            return delay.isNegative() ? null : delay;
        } catch (java.time.DateTimeException _) {
            return null;
        }
    }
    private java.time.Duration retryAfterSeconds(String value) {
        try { return java.time.Duration.ofSeconds(Long.parseLong(value)); }
        catch (NumberFormatException _) { return java.time.Duration.ofDays(2); }
    }
    private CatalogPage normalize(CatalogQuery query, SteamCatalogEnvelope envelope) {
        var response = checkedResponse(query, envelope);
        List<CatalogItem> items = new ArrayList<>();
        long previous = query.afterAppId();
        for (var app : response.apps()) {
            validateApp(app, previous);
            previous = app.appid();
            items.add(new CatalogItem(Long.toString(app.appid()), app.name().strip(), Instant.ofEpochSecond(app.lastModified())));
        }
        validateContinuation(response, previous, items.isEmpty());
        return new CatalogPage(items, previous, response.haveMoreResults());
    }

    private SteamCatalogEnvelope.Response checkedResponse(CatalogQuery query, SteamCatalogEnvelope envelope) {
        if (envelope == null || envelope.response() == null || envelope.response().apps() == null
                || envelope.response().haveMoreResults() == null || envelope.response().apps().size() > query.size()) {
            throw new ProviderException("INVALID_PROVIDER_RESPONSE");
        }
        return envelope.response();
    }

    private void validateApp(SteamCatalogEnvelope.App app, long previous) {
        if (app == null || app.appid() == null || app.appid() <= previous || app.appid() > 4294967295L
                    || app.name() == null || app.name().isBlank() || app.name().length() > 256
                    || app.lastModified() == null || app.lastModified() < 0 || app.lastModified() > 4294967295L) {
            throw new ProviderException("INVALID_PROVIDER_RESPONSE");
        }
    }

    private void validateContinuation(SteamCatalogEnvelope.Response response, long previous, boolean empty) {
        if (response.lastAppId() != null && response.lastAppId() != previous) throw new ProviderException("INVALID_PROVIDER_RESPONSE");
        if (Boolean.TRUE.equals(response.haveMoreResults()) && empty) throw new ProviderException("INVALID_PROVIDER_RESPONSE");
    }
}
