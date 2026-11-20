package com.ltz.recommendation.application.port;

import com.ltz.recommendation.application.port.PersonalizationSource.Actor;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * REC-13/14: başka oyuncuların yalnız izinli tercih sinyalleri. Kaynak User'dır (öneri rızası, ACTIVE, onboarding,
 * Social engel yansıması ve isteğe bağlı arkadaş dışlaması orada uygulanır); Recommendation bu süzgeçleri yeniden
 * yazmaz ve gelen adayı fazladan zenginleştirmez.
 */
public interface PlayerCandidateSource {
    /** İzleyicinin kendi tercihleri; rıza ve hesap durumu çağıran servis tarafından ayrıca doğrulanır. */
    Taste viewer(Actor actor);

    /**
     * Sınırlı sayıda sayfa tarar. {@code truncated} true ise taranmayan aday vardır; sonuç tam havuz iddiası taşımaz.
     */
    Scan candidates(Actor actor, boolean excludeFriends, String genre, String platform);

    record Taste(Set<String> genres, Set<String> platforms, Set<String> playerTypes) {
        public Taste {
            genres = Set.copyOf(genres);
            platforms = Set.copyOf(platforms);
            playerTypes = Set.copyOf(playerTypes);
        }
    }

    record Candidate(UUID userId, Taste taste) {
        public Candidate {
            if (userId == null || taste == null) throw new IllegalArgumentException("Invalid candidate");
        }
    }

    record Scan(List<Candidate> candidates, boolean truncated) {
        public Scan { candidates = List.copyOf(candidates); }
    }
}
