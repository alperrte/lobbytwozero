package com.ltz.event.infrastructure.persistence.repository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Transactional;

import com.ltz.event.application.port.EventSearchCriteria;
import com.ltz.event.domain.entity.Event;
import com.ltz.event.domain.enums.EventPlatform;
import com.ltz.event.domain.enums.EventStatus;
import com.ltz.event.domain.enums.EventType;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.criteria.AbstractQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;

/**
 * Kriter tabanlı etkinlik sorgusu. Tüm değerler bağlı parametre olarak kullanılır (SQL birleştirme yoktur); sıralama ve
 * alan adları koddaki sabitlerdir (kullanıcı girdisinden alan seçilmez). Başlık eşleşmesi büyük/küçük harf duyarsızdır:
 * iki taraf da veritabanında {@code lower()} ile küçültülür (Unicode/Türkçe harfler dahil), kaçış karakteri {@code '!'}'dir.
 * Aynı tablo üzerinde tek koşul kümesi olduğundan her etkinlik en çok bir kez döner.
 */
public class EventSearchRepositoryImpl implements EventSearchRepository {

	private static final char LIKE_ESCAPE = '!';

	@PersistenceContext
	private EntityManager entityManager;

	@Override
	@Transactional(readOnly = true)
	public Page<Event> search(EventSearchCriteria criteria, Pageable pageable) {
		CriteriaBuilder cb = entityManager.getCriteriaBuilder();

		CriteriaQuery<Event> query = cb.createQuery(Event.class);
		Root<Event> root = query.from(Event.class);
		query.select(root)
				.where(predicates(cb, query, root, criteria).toArray(Predicate[]::new))
				.orderBy(cb.asc(root.get("startsAt")), cb.asc(root.get("id")));
		List<Event> content = entityManager.createQuery(query)
				.setFirstResult(Math.toIntExact(pageable.getOffset()))
				.setMaxResults(pageable.getPageSize())
				.getResultList();

		CriteriaQuery<Long> countQuery = cb.createQuery(Long.class);
		Root<Event> countRoot = countQuery.from(Event.class);
		countQuery.select(cb.count(countRoot))
				.where(predicates(cb, countQuery, countRoot, criteria).toArray(Predicate[]::new));
		long total = entityManager.createQuery(countQuery).getSingleResult();

		return new PageImpl<>(content, pageable, total);
	}

	private static List<Predicate> predicates(CriteriaBuilder cb, AbstractQuery<?> query, Root<Event> root,
			EventSearchCriteria criteria) {
		List<Predicate> predicates = new ArrayList<>();
		predicates.add(cb.greaterThan(root.<Instant>get("endsAt"), criteria.now()));
		predicates.add(root.<EventStatus>get("status").in(criteria.statuses()));
		if (criteria.titlePattern() != null) {
			Predicate title = cb.like(cb.lower(root.<String>get("title")), cb.lower(cb.literal(criteria.titlePattern())),
					LIKE_ESCAPE);
			predicates.add(criteria.textGameIds().isEmpty()
					? title
					: cb.or(title, root.<UUID>get("gameId").in(criteria.textGameIds())));
		}
		if (!criteria.types().isEmpty()) {
			predicates.add(root.<EventType>get("type").in(criteria.types()));
		}
		if (!criteria.platforms().isEmpty()) {
			predicates.add(cb.exists(hasAnyPlatform(cb, query, root, criteria)));
		}
		if (criteria.gameId() != null) {
			predicates.add(cb.equal(root.<UUID>get("gameId"), criteria.gameId()));
		}
		if (criteria.from() != null) {
			predicates.add(cb.greaterThan(root.<Instant>get("endsAt"), criteria.from()));
		}
		if (criteria.to() != null) {
			predicates.add(cb.lessThan(root.<Instant>get("startsAt"), criteria.to()));
		}
		return predicates;
	}

	/**
	 * Etkinliğin kendi platformlarından en az biri istenenlerdendir (exists alt sorgusu: birden çok platform eşleşse de
	 * etkinlik tek kez döner; platformu belirtilmemiş etkinliğin satırı olmadığından eşleşmez).
	 */
	private static Subquery<Integer> hasAnyPlatform(CriteriaBuilder cb, AbstractQuery<?> query, Root<Event> root,
			EventSearchCriteria criteria) {
		Subquery<Integer> subquery = query.subquery(Integer.class);
		Root<Event> correlated = subquery.correlate(root);
		Join<Event, EventPlatform> platform = correlated.join("platforms");
		return subquery.select(cb.literal(1)).where(platform.in(criteria.platforms()));
	}
}
