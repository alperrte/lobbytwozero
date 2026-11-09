package com.ltz.messaging.application.service;

import java.util.Map;
import java.util.UUID;

/**
 * Uygulama içi olay: mesaj commit edildikten sonra akışlara iletilir. {@code perRecipient} gönderen dahil aktif üyelere
 * kendi yüklerini eşler (alıntı ve silme hakkı alıcıya göre değişir, MSG-19/24).
 */
public record MessageCreated(Map<UUID, MessageView> perRecipient) {

    public MessageCreated {
        perRecipient = Map.copyOf(perRecipient);
    }
}
