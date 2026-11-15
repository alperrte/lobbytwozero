package com.ltz.recommendation.application.service;

import java.util.Set;
import java.util.UUID;

public record AssistantQuery(String contractVersion, String topic, String intent, String locale, String genre,
        UUID hardwareProfileId, UUID gameId, String platform) {
    public AssistantQuery {
        if (!"assistant-v1".equals(contractVersion) || !Set.of("tr", "en").contains(locale == null ? "" : locale)) {
            throw new IllegalArgumentException("Unsupported assistant contract");
        }
        validateIntent(topic, intent);
        validateFields(topic, genre, hardwareProfileId, gameId, platform);
    }

    private static void validateIntent(String topic, String intent) {
        var allowed = java.util.Map.of("GAME", Set.of("SELECT_GAME"), "HARDWARE", Set.of("ASSESS_GAME"),
                "LTZ_USAGE", Set.of("VIEW_HISTORY", "GIVE_FEEDBACK"));
        if (!allowed.getOrDefault(topic == null ? "" : topic, Set.of()).contains(intent == null ? "" : intent)) {
            throw new IllegalArgumentException("Unsupported assistant topic or intent");
        }
    }

    private static void validateFields(String topic, String genre, UUID hardwareProfileId, UUID gameId, String platform) {
        boolean game = "GAME".equals(topic);
        boolean hardware = "HARDWARE".equals(topic);
        if (genre != null && (!game || !genre.matches("[a-z0-9-]{1,64}"))) throw new IllegalArgumentException("Invalid genre");
        if (!hardware && (hardwareProfileId != null || gameId != null || platform != null)) {
            throw new IllegalArgumentException("Unexpected hardware context");
        }
        if (platform != null && !platform.matches("[a-z0-9-]{1,32}")) throw new IllegalArgumentException("Invalid platform");
    }
}
