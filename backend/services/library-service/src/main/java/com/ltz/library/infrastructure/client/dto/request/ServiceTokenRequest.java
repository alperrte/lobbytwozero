package com.ltz.library.infrastructure.client.dto.request;

/** Auth client-credentials isteği; gizli anahtar hiçbir çıktıda görünmez. */
public record ServiceTokenRequest(String clientId, String clientSecret, String audience) {
    @Override
    public String toString() { return "ServiceTokenRequest[clientId=" + clientId + ", audience=" + audience + "]"; }
}
