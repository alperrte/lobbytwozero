package com.ltz.game.api.dto.response;

/**
 * Yerel detay ve açık güncellik bilgisi; okuma sırasında dış istek yapılmaz. {@code relation}: DLC ↔ ana oyun ilişkisi; ilişkisi
 * olmayan türlerde null. {@code descriptionLanguage}: açıklamanın dili (küçük harf dil kodu; kaynağı IGDB/Steam ise "en"); bilinmiyorsa null.
 * Açıklama çevrilmez: istemci, arayüz dilinden farklıysa bunu belirtir. Eski istemciler yeni alanları yok sayabilir.
 */
public record GameDetailResponse(GameCardResponse game, GameMetadataResponse metadata,
        String description, java.time.LocalDate releaseDate, DetailFreshnessResponse freshness, GameRelationResponse relation,
        String descriptionLanguage) {
    public GameDetailResponse(GameCardResponse game, GameMetadataResponse metadata,
            String description, java.time.LocalDate releaseDate, DetailFreshnessResponse freshness) {
        this(game, metadata, description, releaseDate, freshness, null, null);
    }
    public GameDetailResponse(GameCardResponse game, GameMetadataResponse metadata,
            String description, java.time.LocalDate releaseDate, DetailFreshnessResponse freshness, GameRelationResponse relation) {
        this(game, metadata, description, releaseDate, freshness, relation, null);
    }
    /** İstenen dilde sağlayıcı metni varsa açıklamayı ve dilini değiştirir; ilişki ve diğer alanlar korunur. */
    public GameDetailResponse withDescription(String text, String language) {
        return new GameDetailResponse(game, metadata, text, releaseDate, freshness, relation, language);
    }
    public GameDetailResponse withRelation(GameRelationResponse value) {
        return new GameDetailResponse(game, metadata, description, releaseDate, freshness, value, descriptionLanguage);
    }
}
