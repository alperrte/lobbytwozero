package com.ltz.recommendation.application.port;

import com.ltz.recommendation.application.port.HardwareAssessmentSource.Requirement;
import com.ltz.recommendation.application.port.PersonalizationSource.Actor;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** REC-06/16: aday oyunların sistem gereksinimlerini tek toplu çağrıyla okur (yalnız herkese açık katalog verisi). */
public interface GameMetadataSource {
    Batch requirements(Actor actor, List<UUID> gameIds, String platform);

    /** byGame: görünür her oyun için (boş olabilir) istenen platformun gereksinimleri; unavailable: görünmeyen oyunlar. */
    record Batch(Map<UUID, List<Requirement>> byGame, Set<UUID> unavailable) {
        public Batch {
            byGame = Map.copyOf(byGame);
            unavailable = Set.copyOf(unavailable);
        }
    }
}
