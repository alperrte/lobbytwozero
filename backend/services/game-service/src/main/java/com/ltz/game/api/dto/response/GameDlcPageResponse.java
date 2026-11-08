package com.ltz.game.api.dto.response;

import java.time.LocalDate;
import java.util.List;

/** Bir ana oyunun aktif DLC'leri (çıkış tarihi yeni olan önce, tarihsizler sonda); sayfalıdır. */
public record GameDlcPageResponse(List<Item> items, int page, int size, long totalElements) {
    public record Item(GameCardResponse game, LocalDate releaseDate) { }
}
