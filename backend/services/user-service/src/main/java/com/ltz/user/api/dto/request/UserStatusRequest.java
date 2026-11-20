package com.ltz.user.api.dto.request;

import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Hesap kullanılabilirliği sorgusu (Messaging, Social).
 *
 * @param userIds sorgulanan hesaplar, en çok 100; yinelenenler tek sonuç verir
 */
public record UserStatusRequest(@NotNull @Size(min = 1, max = 100) List<@NotNull UUID> userIds) {
}
