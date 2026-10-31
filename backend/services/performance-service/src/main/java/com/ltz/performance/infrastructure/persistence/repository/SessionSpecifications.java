package com.ltz.performance.infrastructure.persistence.repository;

import com.ltz.performance.application.service.SessionFilter;
import com.ltz.performance.domain.entity.PerformanceSession;
import jakarta.persistence.criteria.Predicate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;

/** Oturum geçmişi filtreleri; sahiplik ({@code userId}) her zaman ilk koşuldur. */
public final class SessionSpecifications {
    private SessionSpecifications() { }

    public static Specification<PerformanceSession> matching(UUID userId, SessionFilter filter) {
        return (root, _, builder) -> {
            var conditions = new ArrayList<Predicate>();
            conditions.add(builder.equal(root.get("userId"), userId));
            if (filter.gameId() != null) conditions.add(builder.equal(root.get("gameId"), filter.gameId()));
            if (filter.hardwareProfileId() != null) {
                conditions.add(builder.equal(root.get("hardwareProfileId"), filter.hardwareProfileId()));
            }
            if (filter.deviceLinkId() != null) {
                conditions.add(builder.equal(root.get("deviceLinkId"), filter.deviceLinkId()));
            }
            if (filter.status() != null) conditions.add(builder.equal(root.get("status"), filter.status()));
            var window = filter.startedAt();
            if (window.from() != null) {
                conditions.add(builder.greaterThanOrEqualTo(root.<Instant>get("startedAt"), window.from()));
            }
            if (window.to() != null) {
                conditions.add(builder.lessThan(root.<Instant>get("startedAt"), window.to()));
            }
            return builder.and(conditions.toArray(Predicate[]::new));
        };
    }
}
