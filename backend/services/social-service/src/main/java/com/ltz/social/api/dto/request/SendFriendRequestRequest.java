package com.ltz.social.api.dto.request;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;

/** Gönderen kimliği gövdede yoktur: yalnız doğrulanmış token'dan alınır. Bilinmeyen alan 400 döner. */
public record SendFriendRequestRequest(@NotNull UUID receiverId) {
}
