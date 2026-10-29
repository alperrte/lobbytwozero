package com.ltz.game.api.controller;

import com.ltz.game.api.dto.response.CatalogSearchResponse;
import com.ltz.game.api.dto.response.CatalogStatusResponse;
import com.ltz.game.application.service.CatalogImportService;
import com.ltz.game.application.service.CatalogSearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController @RequestMapping("/api/v1/game/catalog") @RequiredArgsConstructor
public class CatalogController {
    private final CatalogSearchService search;
    private final CatalogImportService imports;
    @GetMapping
    public CatalogSearchResponse search(@RequestParam String q, @RequestParam(defaultValue="0") int page,
            @RequestParam(defaultValue="20") int size,
            @RequestParam(defaultValue="false") boolean includeUnclassified) { return search.search(q,page,size,includeUnclassified); }
    @GetMapping("/status")
    public CatalogStatusResponse status() { return imports.status(); }
}
