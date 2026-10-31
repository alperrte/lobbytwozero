package com.ltz.performance.api.dto.response;

import com.ltz.performance.domain.entity.SyncConsent;
import java.time.Instant;

/** Kayıt yoksa izin verilmemiş sayılır: {@code granted=false} ve zaman alanları boştur. */
public record SyncConsentResponse(boolean granted, Instant grantedAt, Instant revokedAt) {
    public static SyncConsentResponse none() { return new SyncConsentResponse(false, null, null); }

    public static SyncConsentResponse from(SyncConsent consent) {
        return new SyncConsentResponse(consent.isGranted(), consent.getGrantedAt(), consent.getRevokedAt());
    }
}
