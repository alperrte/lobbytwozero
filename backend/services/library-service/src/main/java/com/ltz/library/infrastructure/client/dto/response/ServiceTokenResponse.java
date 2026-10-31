package com.ltz.library.infrastructure.client.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Auth RFC 6749 §5.1 yanıtı: accessToken, tokenType, expiresIn (sn), scope (boşlukla ayrılmış). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ServiceTokenResponse(String accessToken, String tokenType, Long expiresIn, String scope) {
    @Override
    public String toString() { return "ServiceTokenResponse[accessToken=***, tokenType=" + tokenType + ", expiresIn=" + expiresIn + "]"; }
}
