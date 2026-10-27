package com.ltz.auth.api.dto.response;

public record TotpSetupResponse(String secret, String otpauthUri) {

    @Override
    public String toString() {
        return "TotpSetupResponse[secret=***, otpauthUri=***]";
    }
}
