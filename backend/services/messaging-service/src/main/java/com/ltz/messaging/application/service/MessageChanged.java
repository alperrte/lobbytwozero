package com.ltz.messaging.application.service;

import java.util.Map;
import java.util.UUID;

/**
 * Uygulama içi olay: mesaj değişikliği (herkesten silme, tepki, benden sil) commit edildikten sonra alıcıların açık
 * akışlarına {@code eventName} olayı olarak iletilir; her alıcı kendi yükünü alır.
 */
public record MessageChanged(String eventName, Map<UUID, Object> payloadByRecipient) {

    public MessageChanged {
        payloadByRecipient = Map.copyOf(payloadByRecipient);
    }
}
