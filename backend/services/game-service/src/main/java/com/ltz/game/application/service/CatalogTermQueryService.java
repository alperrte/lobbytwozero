package com.ltz.game.application.service;

import com.ltz.game.api.dto.response.TermListResponse;
import com.ltz.game.domain.enums.TermKind;
import com.ltz.game.domain.exception.InvalidGameRequestException;
import com.ltz.game.infrastructure.persistence.repository.CatalogTermRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Filtre önerileri için terim listesi. Yalnız okuma; sağlayıcıya istek atmaz, veri uydurmaz. */
@Service @RequiredArgsConstructor
public class CatalogTermQueryService {
    static final int MAX_LIMIT = 200;
    private final CatalogTermRepository terms;

    @Transactional(readOnly = true)
    public TermListResponse list(TermKind kind, int limit) {
        if (kind != TermKind.GENRE && kind != TermKind.PLATFORM) throw new InvalidGameRequestException("INVALID_TERM_KIND");
        if (limit < 1 || limit > MAX_LIMIT) throw new InvalidGameRequestException("INVALID_LIMIT");
        return new TermListResponse(kind.name(), terms.publicTerms(kind, PageRequest.of(0, limit)).stream()
                .map(row -> new TermListResponse.Item(row.getCode(), row.getNameTr(), row.getNameEn(), row.getGameCount())).toList());
    }
}
