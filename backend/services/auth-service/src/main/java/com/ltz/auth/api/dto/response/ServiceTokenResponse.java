package com.ltz.auth.api.dto.response;

import com.ltz.auth.application.service.ServiceClientTokenService.IssuedServiceToken;

/** OAuth 2.0 client-credentials yanıt biçimi (RFC 6749 §5.1); token önbelleğe alınmamalıdır (Cache-Control: no-store). */
public record ServiceTokenResponse(String accessToken, String tokenType, long expiresIn, String scope) {

    public static ServiceTokenResponse from(IssuedServiceToken token) {
        return new ServiceTokenResponse(token.accessToken(), "Bearer", token.expiresIn().toSeconds(), token.scope());
    }

    @Override
    public String toString() {
        return "ServiceTokenResponse[accessToken=***, tokenType=" + tokenType + ", expiresIn=" + expiresIn + "]";
    }
}
