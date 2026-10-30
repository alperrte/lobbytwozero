package com.ltz.user.api.dto.request;

import java.util.List;
import java.util.UUID;

import com.ltz.user.domain.enums.PrivacyField;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Servisin gizlilik kararı isteği.
 *
 * @param viewerId izleyen kullanıcı (çağıran servisin doğruladığı token'ın {@code sub} değeri); misafir için {@code null}
 * @param ownerIds kararı istenen profil sahipleri, en çok 100
 */
public record PrivacyDecisionRequest(
        UUID viewerId,
        @NotNull PrivacyField field,
        @NotNull @Size(min = 1, max = 100) List<@NotNull UUID> ownerIds) {
}
