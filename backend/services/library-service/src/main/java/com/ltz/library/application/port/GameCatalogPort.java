package com.ltz.library.application.port;

import java.util.UUID;
import java.util.List;
import java.util.Map;

public interface GameCatalogPort {
    void requireAvailable(UUID gameId);
    Map<UUID, GameSummary> summaries(List<UUID> gameIds);
}
