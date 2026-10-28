package com.ltz.auth.api.dto.response;

import com.ltz.auth.application.service.DeviceCredentialService.IssuedDeviceToken;

/** Cihaz token yanıtı (OAuth 2.0 token yanıt biçimi); önbelleğe alınmamalıdır (Cache-Control: no-store). */
public record DeviceTokenResponse(String accessToken, String tokenType, long expiresIn, String scope) {

    public static DeviceTokenResponse from(IssuedDeviceToken token) {
        return new DeviceTokenResponse(token.accessToken(), "Bearer", token.expiresIn().toSeconds(), token.scope());
    }

    @Override
    public String toString() {
        return "DeviceTokenResponse[accessToken=***, tokenType=" + tokenType + ", expiresIn=" + expiresIn + "]";
    }
}
