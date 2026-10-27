package com.ltz.hardware.infrastructure.persistence.repository;

import com.ltz.hardware.api.dto.response.HardwareReviewPageResponse.RatingSummary;
import com.ltz.hardware.application.port.HardwareReviewAudiencePolicy.Decision;
import com.ltz.hardware.domain.entity.HardwareReview;
import com.ltz.hardware.domain.enums.HardwareReviewStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;

@Repository
public class HardwareReviewSearch {
    private final EntityManager entityManager;
    public HardwareReviewSearch(EntityManager entityManager) { this.entityManager = entityManager; }

    public Specification<HardwareReview> visible(UUID productId, UUID viewer, Decision decision) {
        return (root, query, cb) -> filter(root, cb, productId, viewer, decision);
    }

    private Predicate filter(Root<HardwareReview> root, CriteriaBuilder cb,
            UUID productId, UUID viewer, Decision decision) {
        var own = cb.equal(root.get("userId"), viewer);
        Predicate audience = own;
        if (decision.allowOthers()) {
            audience = decision.blockedAuthors().isEmpty() ? cb.conjunction()
                    : cb.or(own, cb.not(root.get("userId").in(decision.blockedAuthors())));
        }
        return cb.and(cb.equal(root.get("productId"), productId),
                cb.equal(root.get("status"), HardwareReviewStatus.VISIBLE), audience);
    }

    public RatingSummary summary(UUID productId, UUID viewer, Decision decision) {
        var cb = entityManager.getCriteriaBuilder();
        var query = cb.createTupleQuery();
        var root = query.from(HardwareReview.class);
        query.select(cb.tuple(cb.count(root.get("rating")), cb.avg(root.<Integer>get("rating"))))
                .where(filter(root, cb, productId, viewer, decision));
        var result = entityManager.createQuery(query).getSingleResult();
        long count = ((Number) result.get(0)).longValue();
        var average = (Number) result.get(1);
        return new RatingSummary(count, average == null ? null : BigDecimal.valueOf(average.doubleValue()).setScale(2, RoundingMode.HALF_UP));
    }
}
