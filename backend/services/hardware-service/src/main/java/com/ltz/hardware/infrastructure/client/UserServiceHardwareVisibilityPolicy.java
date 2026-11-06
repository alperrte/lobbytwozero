package com.ltz.hardware.infrastructure.client;

import com.ltz.hardware.application.port.HardwareVisibilityPolicy;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** User'ın global görünürlük, arkadaşlık ve çift yönlü engel kararı yerel alan gizliliğiyle birlikte uygulanır. */
@Component
public class UserServiceHardwareVisibilityPolicy implements HardwareVisibilityPolicy {
    private final HardwareClientProperties properties;
    private final HardwareServiceTokenClient tokens;
    private final HardwareHttpClient http;
    private final ObjectMapper mapper;

    public UserServiceHardwareVisibilityPolicy(HardwareClientProperties properties, HardwareServiceTokenClient tokens,
            HardwareHttpClient http, ObjectMapper mapper) {
        this.properties = properties;
        this.tokens = tokens;
        this.http = http;
        this.mapper = mapper;
    }

    @Override public Decision evaluate(UUID viewerId, UUID ownerId) {
        if (viewerId == null || ownerId == null) return new Decision(false, false);
        try {
            var request = Map.of("viewerId", viewerId, "field", "HARDWARE", "ownerIds", List.of(ownerId));
            JsonNode root = mapper.readTree(http.post(properties.userServiceUrl().resolve("/internal/v1/privacy/decisions"),
                    request, tokens.userToken()));
            return decision(root, ownerId);
        } catch (RuntimeException _) {
            return new Decision(false, false);
        }
    }

    private static Decision decision(JsonNode root, UUID ownerId) {
        JsonNode decisions = root == null ? null : root.get("decisions");
        if (decisions == null || !decisions.isArray() || decisions.size() != 1) return new Decision(false, false);
        JsonNode item = decisions.get(0);
        JsonNode owner = item.get("ownerId");
        JsonNode allowed = item.get("allowed");
        JsonNode friends = item.get("friends");
        JsonNode setting = item.get("setting");
        boolean valid = owner != null && owner.isString() && ownerId.toString().equalsIgnoreCase(owner.asString())
                && allowed != null && allowed.isBoolean() && friends != null && friends.isBoolean()
                && setting != null && setting.isString() && List.of("PUBLIC", "FRIENDS", "PRIVATE").contains(setting.asString());
        return valid ? new Decision(allowed.asBoolean(), friends.asBoolean()) : new Decision(false, false);
    }
}
