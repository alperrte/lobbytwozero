package com.ltz.user.domain.enums;

import java.util.Optional;

/** Oyuncu türü (USER-02). Bir profil en çok {@link #MAX_PER_PROFILE} tür seçebilir. */
public enum PlayerType {
    CASUAL,
    COMPETITIVE,
    STORY,
    SOCIAL,
    COLLECTOR;

    public static final int MAX_PER_PROFILE = 2;

    public static Optional<PlayerType> parse(String name) {
        for (PlayerType type : values()) {
            if (type.name().equals(name)) {
                return Optional.of(type);
            }
        }
        return Optional.empty();
    }
}
