package com.ltz.game.domain.enums;

/**
 * Bir oyunun LTZ puan durumu. {@code UNRATED} (doğrulanmış "henüz puan yok") ile {@code UNAVAILABLE}
 * ("puan verisi henüz alınamadı") birbirinden ayrıdır; ikincisi asla 0 puan olarak gösterilmez.
 */
public enum RatingStatus { RATED, UNRATED, UNAVAILABLE }
