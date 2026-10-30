package com.ltz.library.application.port;

import java.util.UUID;

public interface GameCatalogPort {
    void requireAvailable(UUID gameId);
}
