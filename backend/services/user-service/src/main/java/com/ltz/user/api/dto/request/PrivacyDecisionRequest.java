package com.ltz.user.api.dto.request;

import java.util.List;
import java.util.UUID;

import com.ltz.user.domain.enums.DecisionField;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Servisin gizlilik kararı isteği.
 *
 * @param viewerId izleyen kullanıcı (çağıran servisin doğruladığı token'ın {@code sub} değeri); misafir için {@code null}
 * @param field profil bölümü ya da {@code REVIEW_AUTHOR} (yazar içeriği: yalnız hesap durumu ve engel)
 * @param ownerIds kararı istenen profil sahipleri, en çok 100
 */
public record PrivacyDecisionRequest(
        UUID viewerId,
        @NotNull DecisionField field,
        @NotNull @Size(min = 1, max = 100) List<@NotNull UUID> ownerIds) {
}
