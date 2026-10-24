package com.ltz.social.domain.event;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Kullanıcı bir hesabı engelledi. Tüketiciler: messaging-service, lfp-service, community-service (etkileşimleri
 * durdurur). Notification bu olayla bildirim üretmez; engellenen kullanıcıya haber verilmez.
 */
public record UserBlockedEvent(UUID blockerId, UUID blockedId) implements SocialEvent {

    @Override
    public String eventType() {
        return "UserBlocked";
    }

    @Override
    public int eventVersion() {
        return 1;
    }

    @Override
    public String aggregateType() {
        return "UserBlock";
    }

    /** Bileşik anahtarlı kaynağın tek kimliği yoktur; yönlü çiftten kararlı bir UUID türetilir. */
    @Override
    public UUID aggregateId() {
        return UUID.nameUUIDFromBytes((blockerId + ">" + blockedId).getBytes(StandardCharsets.UTF_8));
    }
}
