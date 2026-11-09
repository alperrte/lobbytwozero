package com.ltz.user.application.service;

import java.time.Instant;

import com.ltz.user.domain.entity.UserConsents;

/** Kullanıcının kendi rızaları. Hiç kaydedilmediyse rıza yok, sürüm 0 ve zaman boş. */
public record ConsentsView(boolean recommendation, Instant recommendationChangedAt, long version) {

    static ConsentsView of(UserConsents consents) {
        return new ConsentsView(consents.isRecommendationGranted(), consents.getRecommendationChangedAt(),
                consents.getVersion());
    }
}
