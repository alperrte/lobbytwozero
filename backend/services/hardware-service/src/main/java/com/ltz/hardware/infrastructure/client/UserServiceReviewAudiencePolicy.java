package com.ltz.hardware.infrastructure.client;

import com.ltz.hardware.application.port.HardwareReviewAudiencePolicy;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.Objects;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** REVIEW_AUTHOR profil HARDWARE ayarından bağımsızdır; eksik tek karar bütün dış izni kapatır. */
@Component
public class UserServiceReviewAudiencePolicy implements HardwareReviewAudiencePolicy {
    private static final int BATCH_SIZE = 100;
    private final HardwareClientProperties properties;
    private final HardwareServiceTokenClient tokens;
    private final HardwareHttpClient http;
    private final ObjectMapper mapper;

    public UserServiceReviewAudiencePolicy(HardwareClientProperties properties, HardwareServiceTokenClient tokens,
            HardwareHttpClient http, ObjectMapper mapper) {
        this.properties = properties;
        this.tokens = tokens;
        this.http = http;
        this.mapper = mapper;
    }

    @Override public Decision evaluate(UUID viewerId, Set<UUID> authors) {
        if (viewerId == null || authors == null || authors.size() > MAX_AUTHORS || authors.stream().anyMatch(Objects::isNull)) {
            return Decision.denyOthers();
        }
        if (authors.isEmpty()) return Decision.denyOthers();
        try {
            var ordered = new ArrayList<>(authors);
            ordered.sort(UUID::compareTo);
            var denied = new HashSet<UUID>();
            for (int offset = 0; offset < ordered.size(); offset += BATCH_SIZE) {
                var batch = ordered.subList(offset, Math.min(offset + BATCH_SIZE, ordered.size()));
                var request = Map.of("viewerId", viewerId, "field", "REVIEW_AUTHOR", "ownerIds", batch);
                JsonNode root = mapper.readTree(http.post(properties.userServiceUrl().resolve("/internal/v1/privacy/decisions"),
                        request, tokens.userToken()));
                if (!collect(root, batch, denied)) return Decision.denyOthers();
            }
            return new Decision(true, denied);
        } catch (RuntimeException _) {
            return Decision.denyOthers();
        }
    }

    private static boolean collect(JsonNode root, List<UUID> batch, Set<UUID> denied) {
        JsonNode decisions = root == null ? null : root.get("decisions");
        if (decisions == null || !decisions.isArray() || decisions.size() != batch.size()) return false;
        var remaining = new HashSet<>(batch);
        for (JsonNode item : decisions) {
            if (!typed(item)) return false;
            String owner = item.get("ownerId").asString();
            boolean allowed = item.get("allowed").asBoolean();
            String setting = item.get("setting").asString();
            UUID id = UUID.fromString(owner);
            if (!id.toString().equalsIgnoreCase(owner) || !remaining.remove(id)) return false;
            // User kaynağı pasif hesap için PRIVATE döndürür; PUBLIC dışı izin asla kabul edilmez.
            if (!"PUBLIC".equals(setting) && !("PRIVATE".equals(setting) && !allowed)) return false;
            if (!allowed) denied.add(id);
        }
        return remaining.isEmpty();
    }

    private static boolean typed(JsonNode item) {
        JsonNode owner = item.get("ownerId");
        JsonNode allowed = item.get("allowed");
        JsonNode friends = item.get("friends");
        JsonNode setting = item.get("setting");
        return owner != null && owner.isString() && allowed != null && allowed.isBoolean()
                && friends != null && friends.isBoolean() && setting != null && setting.isString();
    }
}
