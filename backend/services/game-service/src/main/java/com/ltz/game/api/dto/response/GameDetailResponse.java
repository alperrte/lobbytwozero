package com.ltz.game.api.dto.response;

/** Faz 1 ortak detay kabuğu; zenginleştirme bu aşamada yapılmaz. */
public record GameDetailResponse(GameCardResponse game, GameMetadataResponse metadata) { }
