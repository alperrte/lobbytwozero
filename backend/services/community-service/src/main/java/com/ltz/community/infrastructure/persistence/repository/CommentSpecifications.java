package com.ltz.community.infrastructure.persistence.repository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import com.ltz.community.domain.entity.Comment;
import com.ltz.community.domain.enums.CommentStatus;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;

/**
 * Yorum listeleri: bir gönderinin üst düzey yorumları veya bir yorumun doğrudan yanıtları, eskiden yeniye
 * (keyset). Yalnız VISIBLE yorumlar ve yanıtı olan silinmiş (DELETED) yorumlar (ilişki korunsun diye) listelenir;
 * HIDDEN yorumlar listelenmez. Tüm değerler parametre olarak bağlanır.
 */
public final class CommentSpecifications {

	private static final String CREATED_AT = "createdAt";
	private static final String ID = "id";
	private static final String PARENT_COMMENT_ID = "parentCommentId";

	private CommentSpecifications() {
	}

	public static Specification<Comment> topLevelOf(UUID postId, Set<UUID> excludedAuthorIds, Instant afterCreatedAt,
			UUID afterId) {
		return (root, query, cb) -> {
			List<Predicate> predicates = new ArrayList<>();
			predicates.add(cb.equal(root.<UUID>get("postId"), postId));
			predicates.add(cb.isNull(root.get(PARENT_COMMENT_ID)));
			predicates.add(listed(root, query.subquery(Integer.class), cb));
			addExcludedAuthors(predicates, root, cb, excludedAuthorIds);
			addAfter(predicates, root, cb, afterCreatedAt, afterId);
			return cb.and(predicates.toArray(Predicate[]::new));
		};
	}

	public static Specification<Comment> repliesOf(UUID parentCommentId, Set<UUID> excludedAuthorIds,
			Instant afterCreatedAt, UUID afterId) {
		return (root, query, cb) -> {
			List<Predicate> predicates = new ArrayList<>();
			predicates.add(cb.equal(root.<UUID>get(PARENT_COMMENT_ID), parentCommentId));
			predicates.add(listed(root, query.subquery(Integer.class), cb));
			addExcludedAuthors(predicates, root, cb, excludedAuthorIds);
			addAfter(predicates, root, cb, afterCreatedAt, afterId);
			return cb.and(predicates.toArray(Predicate[]::new));
		};
	}

	public static Sort oldestFirst() {
		return Sort.by(Sort.Order.asc(CREATED_AT), Sort.Order.asc(ID));
	}

	/** VISIBLE, ya da silinmiş ama en az bir yanıtı olan (yanıt ilişkisini taşıyan) yorum. */
	private static Predicate listed(Root<Comment> root, Subquery<Integer> hasReply, CriteriaBuilder cb) {
		Root<Comment> child = hasReply.from(Comment.class);
		hasReply.select(cb.literal(1)).where(cb.equal(child.get(PARENT_COMMENT_ID), root.get(ID)));
		return cb.or(
				cb.equal(root.get("status"), CommentStatus.VISIBLE),
				cb.and(cb.equal(root.get("status"), CommentStatus.DELETED), cb.exists(hasReply)));
	}

	/** Çağıranla engelleme ilişkisi olan yazarların yorumları listelenmez (COM-21). */
	private static void addExcludedAuthors(List<Predicate> predicates, Root<Comment> root, CriteriaBuilder cb,
			Set<UUID> excludedAuthorIds) {
		if (!excludedAuthorIds.isEmpty()) {
			predicates.add(cb.not(root.get("authorId").in(excludedAuthorIds)));
		}
	}

	private static void addAfter(List<Predicate> predicates, Root<Comment> root, CriteriaBuilder cb,
			Instant afterCreatedAt, UUID afterId) {
		if (afterCreatedAt == null) {
			return;
		}
		predicates.add(cb.or(
				cb.greaterThan(root.<Instant>get(CREATED_AT), afterCreatedAt),
				cb.and(cb.equal(root.get(CREATED_AT), afterCreatedAt), cb.greaterThan(root.<UUID>get(ID), afterId))));
	}
}
