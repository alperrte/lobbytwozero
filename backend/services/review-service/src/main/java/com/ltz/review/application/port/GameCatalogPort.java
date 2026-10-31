package com.ltz.review.application.port;

import java.util.UUID;

public interface GameCatalogPort {
    void requireAvailable(UUID gameId);
}
