package com.ltz.game.application.service;

import com.ltz.game.api.dto.response.CatalogSearchResponse;
import com.ltz.game.application.mapper.GameViewMapper;
import com.ltz.game.domain.enums.GameStatus;
import com.ltz.game.domain.enums.GameType;
import com.ltz.game.infrastructure.persistence.repository.GameRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service @RequiredArgsConstructor
public class CatalogSearchService {
    private final GameRepository games;
    private final GameViewMapper mapper;
    private final CatalogImportService imports;
    @Transactional(readOnly=true)
    public CatalogSearchResponse search(String query, int page, int size) { return searchIndex(query,page,size,false); }
    @Transactional(readOnly=true)
    public CatalogSearchResponse search(String query, int page, int size, boolean includeUnclassified) {
        return searchIndex(query, page, size, includeUnclassified);
    }
    private CatalogSearchResponse searchIndex(String query, int page, int size, boolean includeUnclassified) {
        if (query == null || query.isBlank() || query.length() > 128 || page < 0 || size < 1 || size > 100)
            throw new IllegalArgumentException("Geçersiz indeks araması.");
        String pattern = CatalogImportService.normalize(query).replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
        var result = games.searchIndex(pattern, GameStatus.ACTIVE, GameType.MAIN_GAME, GameType.UNKNOWN, includeUnclassified,
                PageRequest.of(page,size,Sort.by("normalizedTitle").ascending().and(Sort.by("id"))));
        return new CatalogSearchResponse(result.stream().map(mapper::card).toList(), page, size,
                result.getTotalElements(), imports.status().lastSuccessfulAt(), null);
    }
}
