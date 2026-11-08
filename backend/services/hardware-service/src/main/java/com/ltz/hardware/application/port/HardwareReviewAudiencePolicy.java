package com.ltz.hardware.application.port;

import java.util.Set;
import java.util.UUID;

/** Karar yalnız verilen tam yazar kümesi içindir; adaylar sayfalama öncesinde alınır. */
public interface HardwareReviewAudiencePolicy {
    int MAX_AUTHORS = 1000;
    record Decision(boolean allowOthers, Set<UUID> blockedAuthors) {
        public Decision {
            if (blockedAuthors == null || blockedAuthors.size() > MAX_AUTHORS) {
                throw new IllegalArgumentException("Geçersiz görünürlük kararı");
            }
            blockedAuthors = Set.copyOf(blockedAuthors);
        }
        public boolean permits(UUID viewer, UUID author) {
            return viewer.equals(author) || (allowOthers && !blockedAuthors.contains(author));
        }
        public static Decision denyOthers() { return new Decision(false, Set.of()); }
    }
    Decision evaluate(UUID viewerId, Set<UUID> authors);
}
