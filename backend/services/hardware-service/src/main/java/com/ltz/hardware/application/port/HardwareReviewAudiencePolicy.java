package com.ltz.hardware.application.port;

import java.util.Set;
import java.util.UUID;

/** Trusted global UGC permission and complete two-way block snapshot, never client claims. */
public interface HardwareReviewAudiencePolicy {
    record Decision(boolean allowOthers, Set<UUID> blockedAuthors) {
        public Decision {
            if (blockedAuthors == null || blockedAuthors.size() > 1000) {
                throw new IllegalArgumentException("Geçersiz görünürlük kararı");
            }
            blockedAuthors = Set.copyOf(blockedAuthors);
        }
        public boolean permits(UUID viewer, UUID author) {
            return viewer.equals(author) || (allowOthers && !blockedAuthors.contains(author));
        }
        public static Decision denyOthers() { return new Decision(false, Set.of()); }
    }
    Decision evaluate(UUID viewerId);
}
