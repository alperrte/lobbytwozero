package com.ltz.community.infrastructure.persistence.repository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import com.ltz.community.application.port.PostSearchCriteria;
import com.ltz.community.application.port.PostSearchCriteria.FeedPosition;
import com.ltz.community.domain.entity.Post;
import com.ltz.community.domain.enums.PostSort;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;

/** Gönderi akışı sorgusu; tüm değerler parametre olarak bağlanır, sıralama alanları sabittir (SQL birleştirme yok). */
public final class PostSpecifications {

	private static final char LIKE_ESCAPE = '\\';
	private static final String CREATED_AT = "createdAt";
	private static final String POPULARITY_SCORE = "popularityScore";

	private PostSpecifications() {
	}

	public static Specification<Post> matching(PostSearchCriteria criteria) {
		return (root, query, cb) -> {
			List<Predicate> predicates = new ArrayList<>();
			predicates.add(root.get("status").in(criteria.statuses()));
			if (criteria.authorId() != null) {
				predicates.add(cb.equal(root.<UUID>get("authorId"), criteria.authorId()));
			}
			if (criteria.text() != null) {
				// ix_posts_body_trgm (gin, lower(body) gin_trgm_ops) bu ifadeyi kullanır.
				predicates.add(cb.like(cb.lower(root.<String>get("body")), containsPattern(criteria.text()), LIKE_ESCAPE));
			}
			if (criteria.gameId() != null) {
				predicates.add(cb.equal(root.<UUID>get("gameId"), criteria.gameId()));
			}
			if (criteria.category() != null) {
				predicates.add(cb.equal(root.get("category"), criteria.category()));
			}
			if (criteria.createdFrom() != null) {
				predicates.add(cb.greaterThanOrEqualTo(root.<Instant>get(CREATED_AT), criteria.createdFrom()));
			}
			if (criteria.createdTo() != null) {
				predicates.add(cb.lessThan(root.<Instant>get(CREATED_AT), criteria.createdTo()));
			}
			if (criteria.after() != null) {
				predicates.add(after(cb, root, criteria.sort(), criteria.after()));
			}
			return cb.and(predicates.toArray(Predicate[]::new));
		};
	}

	/** Sıralamadaki anahtar sırasına göre "bu konumdan sonra" koşulu (keyset). */
	private static Predicate after(CriteriaBuilder cb, Root<Post> root, PostSort sort, FeedPosition after) {
		Predicate createdAfterInSortOrder = switch (sort) {
			case OLDEST -> cb.or(
					cb.greaterThan(root.<Instant>get(CREATED_AT), after.createdAt()),
					cb.and(cb.equal(root.get(CREATED_AT), after.createdAt()),
							cb.greaterThan(root.<UUID>get("id"), after.id())));
			case NEWEST, POPULAR -> cb.or(
					cb.lessThan(root.<Instant>get(CREATED_AT), after.createdAt()),
					cb.and(cb.equal(root.get(CREATED_AT), after.createdAt()),
							cb.lessThan(root.<UUID>get("id"), after.id())));
		};
		if (sort != PostSort.POPULAR) {
			return createdAfterInSortOrder;
		}
		return cb.or(
				cb.lessThan(root.<Integer>get(POPULARITY_SCORE), after.score()),
				cb.and(cb.equal(root.get(POPULARITY_SCORE), after.score()), createdAfterInSortOrder));
	}

	public static Sort sort(PostSort sort) {
		return switch (sort) {
			case NEWEST -> Sort.by(Sort.Order.desc(CREATED_AT), Sort.Order.desc("id"));
			case OLDEST -> Sort.by(Sort.Order.asc(CREATED_AT), Sort.Order.asc("id"));
			case POPULAR -> Sort.by(Sort.Order.desc(POPULARITY_SCORE), Sort.Order.desc(CREATED_AT),
					Sort.Order.desc("id"));
		};
	}

	/** Kullanıcı metnindeki LIKE joker karakterleri (%, _) ve kaçış karakteri düz metin olarak aranır. */
	static String containsPattern(String text) {
		String escaped = text.toLowerCase(Locale.ROOT)
				.replace("\\", "\\\\")
				.replace("%", "\\%")
				.replace("_", "\\_");
		return "%" + escaped + "%";
	}
}
