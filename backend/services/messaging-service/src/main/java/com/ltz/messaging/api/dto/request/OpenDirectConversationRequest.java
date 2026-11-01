package com.ltz.messaging.api.dto.request;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;

/** Birebir sohbet açılacak karşı taraf. Açan kullanıcı token'dan alınır. */
public record OpenDirectConversationRequest(@NotNull UUID userId) {
}
