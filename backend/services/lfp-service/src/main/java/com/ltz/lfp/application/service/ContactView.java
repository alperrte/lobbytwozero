package com.ltz.lfp.application.service;

import java.util.UUID;

/**
 * LFP-16: ilan sahibiyle iletişim izni. LFP mesaj altyapısı kurmaz; istemci {@code recipientId} ile messaging-service'in
 * birebir sohbet ucunu ({@code POST /api/v1/messaging/conversations/direct}) çağırır.
 */
public record ContactView(UUID listingId, UUID recipientId) {
}
