package com.ltz.game.api.dto.response;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record GameDiscoveryResponse(List<Item> items, int page, int size, long totalElements,
        String sort, String direction, String popularityBasis) {
    public record Item(GameCardResponse game, LocalDate releaseDate, Boolean earlyAccess,
            List<String> languages, Integer editorialRank, Instant popularityUpdatedAt) { }
}
