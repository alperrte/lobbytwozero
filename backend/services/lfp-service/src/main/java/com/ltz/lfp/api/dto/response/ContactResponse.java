package com.ltz.lfp.api.dto.response;

import java.util.UUID;

import com.ltz.lfp.application.service.ContactView;

/**
 * İlan sahibiyle iletişim izni (LFP-16). İstemci {@code recipientId} ile messaging-service'te birebir sohbet açar:
 * {@code POST /api/v1/messaging/conversations/direct} gövdesi {@code {"userId": recipientId}}. LFP mesaj saklamaz.
 */
public record ContactResponse(UUID listingId, UUID recipientId) {

    public static ContactResponse from(ContactView view) {
        return new ContactResponse(view.listingId(), view.recipientId());
    }
}
