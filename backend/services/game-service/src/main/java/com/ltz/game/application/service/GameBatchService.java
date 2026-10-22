package com.ltz.game.application.service;

import com.ltz.game.api.dto.response.GameBatchResponse;
import com.ltz.game.api.dto.response.GameCardResponse;
import com.ltz.game.infrastructure.persistence.repository.GameBatchRepository;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * GAME-15: Library/Review gibi servislerin çoklu kart okuması. Yalnız herkese açık (ACTIVE) kart verisi döner;
 * kişisel, yönetimsel veya LTZ puanı verisi içermez. Sabit sayıda SQL ile çalışır (N+1 yok).
 */
@Service @RequiredArgsConstructor @Transactional(readOnly = true)
public class GameBatchService {
    static final int MAX_IDS = 50;
    private final GameBatchRepository repository;

    public GameBatchResponse cards(List<UUID> requested) {
        if (requested == null || requested.isEmpty() || requested.size() > MAX_IDS || requested.stream().anyMatch(java.util.Objects::isNull))
            throw new IllegalArgumentException("Geçersiz toplu istek.");
        LinkedHashSet<UUID> ids = new LinkedHashSet<>(requested);
        Map<UUID, GameCardResponse> found = repository.cards(ids);
        return new GameBatchResponse(ids.stream().map(found::get).filter(java.util.Objects::nonNull).toList(),
                ids.stream().filter(id -> !found.containsKey(id)).toList());
    }
}
