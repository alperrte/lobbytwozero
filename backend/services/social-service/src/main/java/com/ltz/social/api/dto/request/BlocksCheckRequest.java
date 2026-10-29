package com.ltz.social.api.dto.request;

import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Toplu engel sorgusu: bir kullanıcı ve en fazla 100 diğer kullanıcı. Bilinmeyen alan 400 döner. */
public record BlocksCheckRequest(@NotNull UUID userId,
                                 @NotEmpty @Size(max = BlocksCheckRequest.MAX_OTHER_USERS) List<@NotNull UUID> otherUserIds) {

    public static final int MAX_OTHER_USERS = 100;
}
