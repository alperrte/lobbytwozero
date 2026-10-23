package com.ltz.game.infrastructure.client.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SteamCatalogEnvelope(Response response) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Response(List<App> apps, @JsonProperty("last_appid") Long lastAppId,
            @JsonProperty("have_more_results") Boolean haveMoreResults) { }
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record App(Long appid, String name, @JsonProperty("last_modified") Long lastModified) { }
}
