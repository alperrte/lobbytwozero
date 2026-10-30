package com.ltz.user.application.service;

import java.util.List;
import java.util.UUID;

/**
 * Başka birinin (ya da misafirin) gördüğü profil. Temel kart (ad, avatar, kapak) her zaman doludur: profil bütünüyle
 * gizlenemez. {@code details} yalnız ayrıntı izni olan izleyicide doludur.
 */
public record PublicProfileView(UUID userId, String username, String displayName, MediaView avatar, MediaView cover,
                                Details details, Sections visible, Summary summary) {

    /** Alan gizliliğine tabi olmayan profil ayrıntıları. */
    public record Details(String bio, List<String> playerTypes, List<String> favoriteGenres,
                          List<String> favoritePlatforms, String country) {
    }

    /** İzleyicinin görebileceği bölümler; istemci diğer servislere yalnız bunlar için gider. */
    public record Sections(boolean friendsList, boolean library, boolean hardware, boolean performance) {
    }

    /** USER-12 özetleri. Sayı yalnız {@code AVAILABLE} iken doludur; sayı hiçbir zaman uydurulmaz. */
    public record Summary(Count friends, Library library, Count reviews) {
    }

    public record Count(SummaryStatus status, Integer count) {
    }

    public record Library(SummaryStatus status, Integer collection, Integer completed, Integer favorite) {
    }

    /** {@code HIDDEN}: izleyici göremez. {@code UNAVAILABLE}: kaynak servisten henüz veri yok. */
    public enum SummaryStatus {
        AVAILABLE,
        HIDDEN,
        UNAVAILABLE
    }
}
