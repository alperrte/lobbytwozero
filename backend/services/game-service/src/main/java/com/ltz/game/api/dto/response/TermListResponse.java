package com.ltz.game.api.dto.response;

import java.util.List;

/** Herkese açık terim listesi: yalnız public katalogdaki (ACTIVE + MAIN_GAME) oyunlara bağlı terimler. */
public record TermListResponse(String kind, List<Item> items) {
    /** {@code code} katalog filtresinde kullanılan değerdir; adlar boş olabilir (sağlayıcı vermediyse uydurulmaz). */
    public record Item(String code, String nameTr, String nameEn, long gameCount) { }
}
