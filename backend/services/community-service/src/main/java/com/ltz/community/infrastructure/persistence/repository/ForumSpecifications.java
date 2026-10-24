package com.ltz.community.infrastructure.persistence.repository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import com.ltz.community.domain.entity.Forum;
import com.ltz.community.domain.enums.ForumStatus;

import jakarta.persistence.criteria.Predicate;

/** Herkese açık forum listesi: yalnız VISIBLE forumlar, isteğe bağlı oyun filtresi, eskiden yeniye keyset. */
public final class ForumSpecifications {

	private static final String CREATED_AT = "createdAt";
	private static final String ID = "id";

	private ForumSpecifications() {
	}

	public static Specification<Forum> visible(UUID gameId, Instant afterCreatedAt, UUID afterId) {
		return (root, query, cb) -> {
			List<Predicate> predicates = new ArrayList<>();
			predicates.add(cb.equal(root.get("status"), ForumStatus.VISIBLE));
			if (gameId != null) {
				predicates.add(cb.equal(root.<UUID>get("gameId"), gameId));
			}
			if (afterCreatedAt != null) {
				predicates.add(cb.or(
						cb.greaterThan(root.<Instant>get(CREATED_AT), afterCreatedAt),
						cb.and(cb.equal(root.get(CREATED_AT), afterCreatedAt),
								cb.greaterThan(root.<UUID>get(ID), afterId))));
			}
			return cb.and(predicates.toArray(Predicate[]::new));
		};
	}

	public static Sort oldestFirst() {
		return Sort.by(Sort.Order.asc(CREATED_AT), Sort.Order.asc(ID));
	}
}
