package com.ltz.messaging.api.dto.request;

import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Mesaj iletme (MSG-20). {@code clientMessageId} istemcinin istek başına ürettiği tekil kimliktir: aynı istek
 * tekrarlanırsa yeni mesaj oluşmaz. {@code targetConversationIds}: 1–5 farklı sohbet; tekrar eden hedef
 * {@code 400 DUPLICATE_FORWARD_TARGET}.
 */
public record ForwardMessageRequest(
        @NotBlank @Size(max = 64) @Pattern(regexp = "[A-Za-z0-9_-]+") String clientMessageId,
        @NotNull @Size(min = 1, max = 5) List<@NotNull UUID> targetConversationIds) {
}
