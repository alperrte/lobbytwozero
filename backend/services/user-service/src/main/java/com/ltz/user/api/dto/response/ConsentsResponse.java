package com.ltz.user.api.dto.response;

import java.time.Instant;

import com.ltz.user.application.service.ConsentsView;

/** Kullanıcının kendi rızaları. {@code recommendationChangedAt} hiç karar verilmediyse boştur. */
public record ConsentsResponse(boolean recommendation, Instant recommendationChangedAt, long version) {

    public static ConsentsResponse from(ConsentsView view) {
        return new ConsentsResponse(view.recommendation(), view.recommendationChangedAt(), view.version());
    }
}
