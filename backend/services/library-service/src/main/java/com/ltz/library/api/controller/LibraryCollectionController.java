package com.ltz.library.api.controller;

import com.ltz.library.api.dto.response.LibraryCollectionPageResponse;
import com.ltz.library.api.dto.response.LibraryStatisticsResponse;
import com.ltz.library.application.service.LibraryCollectionService;
import com.ltz.library.domain.enums.CollectionSort;
import com.ltz.library.domain.enums.SortDirection;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/library/entries")
public class LibraryCollectionController {
    private final LibraryCollectionService collection;

    public LibraryCollectionController(LibraryCollectionService collection) { this.collection = collection; }

    @GetMapping
    public LibraryCollectionPageResponse list(@AuthenticationPrincipal Jwt token,
            @RequestParam(required = false) Boolean favorite,
            @RequestParam(required = false) Boolean completed,
            @RequestParam(defaultValue = "ADDED_AT") CollectionSort sort,
            @RequestParam(defaultValue = "DESC") SortDirection direction,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        return collection.list(UUID.fromString(token.getSubject()), favorite, completed, sort, direction, page, size);
    }

    @GetMapping("/statistics")
    public LibraryStatisticsResponse statistics(@AuthenticationPrincipal Jwt token) {
        return collection.statistics(UUID.fromString(token.getSubject()));
    }
}
