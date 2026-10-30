package com.ltz.user.api.dto.response;

import java.time.Instant;
import java.util.List;

import com.ltz.user.application.service.OnboardingView;

/** İlk kurulum durumu; {@code missing} tamamlanması için eksik adımlar, {@code version} tamamlama isteğinde gönderilir. */
public record OnboardingResponse(boolean completed, Instant completedAt, List<String> missing, long version) {

    public static OnboardingResponse from(OnboardingView view) {
        return new OnboardingResponse(view.completed(), view.completedAt(), view.missing(), view.profileVersion());
    }
}
