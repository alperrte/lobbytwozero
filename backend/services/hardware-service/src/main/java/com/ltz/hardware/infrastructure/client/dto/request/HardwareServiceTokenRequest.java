package com.ltz.hardware.infrastructure.client.dto.request;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.UUID;

/** Credential yalnız Auth istek gövdesine yazılır; tanısal metinlerde maskelenir. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record HardwareServiceTokenRequest(String clientId, String clientSecret, String audience, UUID accountId) {
    @Override public String toString() {
        return "HardwareServiceTokenRequest[clientId=" + clientId + ", clientSecret=***, audience=" + audience + "]";
    }
}
