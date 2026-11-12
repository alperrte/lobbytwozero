package com.ltz.messaging.api.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Tepki (MSG-23): tek bir emoji (ZWJ dizisi, ten rengi, bayrak, keycap dahil), UTF-16 birimiyle en çok 32. Tek emoji
 * kuralı sunucuda denetlenir: {@code 400 INVALID_REACTION}.
 */
public record ReactionRequest(@NotNull @Size(min = 1, max = 32) String emoji) {
}
