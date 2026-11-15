package com.ltz.recommendation.api.dto.request;

import com.ltz.recommendation.application.service.AssistantQuery;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.util.UUID;

public record AssistantRequest(@NotBlank @Pattern(regexp = "assistant-v1") String contractVersion,
        @NotBlank @Pattern(regexp = "GAME|HARDWARE|LTZ_USAGE") String topic,
        @NotBlank @Pattern(regexp = "SELECT_GAME|ASSESS_GAME|VIEW_HISTORY|GIVE_FEEDBACK") String intent,
        @NotBlank @Pattern(regexp = "tr|en") String locale,
        @Pattern(regexp = "[a-z0-9-]{1,64}") String genre, UUID hardwareProfileId, UUID gameId,
        @Pattern(regexp = "[a-z0-9-]{1,32}") String platform) {
    public AssistantQuery toQuery() {
        return new AssistantQuery(contractVersion, topic, intent, locale, genre, hardwareProfileId, gameId, platform);
    }
}
