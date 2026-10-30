package com.ltz.user.api.dto.response;

import java.util.List;
import java.util.UUID;

import com.ltz.user.application.service.ProfileCardView;

/** Profil kartları listesi (arama sonuçları ya da toplu okuma). */
public record ProfileCardsResponse(List<Card> items) {

    /** Liste kartı: ad ve avatar (küçük/büyük adres). */
    public record Card(UUID userId, String username, String displayName, MediaResponse avatar) {
    }

    public static ProfileCardsResponse from(List<ProfileCardView> cards) {
        return new ProfileCardsResponse(cards.stream()
                .map(card -> new Card(card.userId(), card.username(), card.displayName(),
                        MediaResponse.from(card.avatar())))
                .toList());
    }
}
