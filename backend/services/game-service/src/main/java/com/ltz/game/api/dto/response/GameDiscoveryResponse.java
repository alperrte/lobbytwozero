package com.ltz.game.api.dto.response;

import com.ltz.game.domain.enums.RatingStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * {@code ratingsSyncedAt}: LTZ puan projeksiyonunun son hatasız tamamlanan tarama zamanı; hiç tamamlanmadıysa
 * {@code null} (bu durumda her öğenin puan durumu {@code UNAVAILABLE}'dır).
 * {@code includeAdultApplied}: yetişkin içerik (+18) bu yanıta katıldı mı. İstek {@code includeAdult=true} gönderip kullanıcı yaşı doğrulanmış değilse
 * parametre sessizce yok sayılır ve bu alan {@code false} döner (istemci "yaşını doğrula" ipucunu bununla gösterir).
 */
public record GameDiscoveryResponse(List<Item> items, int page, int size, long totalElements,
        String sort, String direction, String popularityBasis, Instant ratingsSyncedAt, boolean includeAdultApplied) {
    /** {@code earlyAccess}, {@code free}, {@code comingSoon} ve {@code adult}: Steam'in mağaza bilgisi; {@code null} = bilinmiyor (false değildir). */
    public record Item(GameCardResponse game, LocalDate releaseDate, Boolean earlyAccess, Boolean free, Boolean comingSoon, Boolean adult,
            List<String> languages, Integer editorialRank, Instant popularityUpdatedAt, Rating rating) { }

    /**
     * {@code RATED}: ortalama ve sayı dolu. {@code UNRATED}: doğrulanmış "henüz puan yok" (averageRating null,
     * ratingCount 0). {@code UNAVAILABLE}: puan verisi henüz alınamadı (averageRating ve ratingCount null; 0 değildir).
     */
    public record Rating(RatingStatus status, BigDecimal averageRating, Long ratingCount) { }
}
