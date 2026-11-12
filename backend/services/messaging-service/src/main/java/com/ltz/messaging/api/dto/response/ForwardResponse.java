package com.ltz.messaging.api.dto.response;

import java.util.List;

import com.ltz.messaging.application.service.ForwardResult;

/** İletme yanıtı (MSG-20): hedef sırasıyla oluşan mesajlar. */
public record ForwardResponse(List<MessageResponse> messages) {

    public static ForwardResponse from(ForwardResult result) {
        return new ForwardResponse(result.messages().stream().map(MessageResponse::from).toList());
    }
}
