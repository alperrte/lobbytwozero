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
import com.ltz.community.domain.entity.Forum;
import com.ltz.community.domain.entity.Post;
import com.ltz.community.domain.enums.ForumStatus;
import com.ltz.community.domain.enums.PostSort;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;

/** Gönderi akışı sorgusu; tüm değerler parametre olarak bağlanır, sıralama alanları sabittir (SQL birleştirme yok). */
public final class PostSpecifications {

	private static final char LIKE_ESCAPE = '\\';
	private static final String CREATED_AT = "createdAt";
	private static final String POPULARITY_SCORE = "popularityScore";
	private static final String FORUM_ID = "forumId";

	private PostSpecifications() {
	}

	public static Specification<Post> matching(PostSearchCriteria criteria) {
		return (root, query, cb) -> {
			List<Predicate> predicates = new ArrayList<>();
			predicates.add(root.get("status").in(criteria.statuses()));
			predicates.add(inVisibleForumOrNone(root, query.subquery(Integer.class), cb));
			if (criteria.forumId() != null) {
				predicates.add(cb.equal(root.<UUID>get(FORUM_ID), criteria.forumId()));
			}
			if (!criteria.excludedAuthorIds().isEmpty()) {
				predicates.add(cb.not(root.get("authorId").in(criteria.excludedAuthorIds())));
			}
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
			addCreatedBetween(predicates, root, cb, criteria.createdFrom(), criteria.createdTo());
			if (criteria.after() != null) {
				predicates.add(after(cb, root, criteria.sort(), criteria.after()));
			}
			return cb.and(predicates.toArray(Predicate[]::new));
		};
	}

	/** Oluşturulma zamanı aralığı: {@code from} dahil, {@code to} hariç; null sınır uygulanmaz. */
	private static void addCreatedBetween(List<Predicate> predicates, Root<Post> root, CriteriaBuilder cb, Instant from,
			Instant to) {
		if (from != null) {
			predicates.add(cb.greaterThanOrEqualTo(root.<Instant>get(CREATED_AT), from));
		}
		if (to != null) {
			predicates.add(cb.lessThan(root.<Instant>get(CREATED_AT), to));
		}
	}

	/** Gizli (HIDDEN) bir forumun konuları hiçbir listede görünmez; forum dışı gönderiler etkilenmez. */
	private static Predicate inVisibleForumOrNone(Root<Post> root, Subquery<Integer> visibleForum, CriteriaBuilder cb) {
		Root<Forum> forum = visibleForum.from(Forum.class);
		visibleForum.select(cb.literal(1)).where(
				cb.equal(forum.get("id"), root.get(FORUM_ID)),
				cb.equal(forum.get("status"), ForumStatus.VISIBLE));
		return cb.or(cb.isNull(root.get(FORUM_ID)), cb.exists(visibleForum));
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
