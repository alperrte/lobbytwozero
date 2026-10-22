package com.ltz.auth.api.dto.response;

import java.util.List;

/** Kurtarma kodları yalnız bu yanıtta bir kez görünür; sunucuda yalnız özetleri tutulur. */
public record RecoveryCodesResponse(List<String> recoveryCodes) {

    public RecoveryCodesResponse {
        recoveryCodes = List.copyOf(recoveryCodes);
    }

    @Override
    public String toString() {
        return "RecoveryCodesResponse[recoveryCodes=***]";
    }
}
