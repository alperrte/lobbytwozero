package com.ltz.game.api.dto.response;

/** Yerel detay ve açık güncellik bilgisi; okuma sırasında dış istek yapılmaz. */
public record GameDetailResponse(GameCardResponse game, GameMetadataResponse metadata,
        String description, java.time.LocalDate releaseDate, DetailFreshnessResponse freshness) { }
