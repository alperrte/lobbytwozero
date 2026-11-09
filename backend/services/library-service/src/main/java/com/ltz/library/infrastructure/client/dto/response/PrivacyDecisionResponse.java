package com.ltz.library.infrastructure.client.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import java.util.UUID;

/** User kararı. Yalnız {@code ownerId} ve {@code allowed} okunur; {@code setting} ve {@code friends} karar yerine geçmez. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PrivacyDecisionResponse(List<Decision> decisions) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Decision(UUID ownerId, Boolean allowed) { }
}
