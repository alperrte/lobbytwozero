package com.ltz.lfp.infrastructure.persistence.repository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import com.ltz.lfp.domain.entity.Listing;
import com.ltz.lfp.domain.enums.ListingStatus;
import com.ltz.lfp.domain.enums.Platform;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;

/**
 * İlan listesi ve aramasının (LFP-10, LFP-11) süzgeçleri. Tüm değerler parametre olarak bağlanır (SQL metni
 * birleştirilmez); LIKE ifadelerinde {@code %}, {@code _} ve {@code \} kaçışlanır. Sıra her zaman en yeni önce:
 * {@code created_at desc, id desc} (keyset sayfalamayla uyumlu, indeks {@code ix_lfp_listings_open_created}).
 */
public final class ListingSpecifications {

    public static final Sort NEWEST_FIRST = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));

    private static final String STATUS = "status";
    private static final String EXPIRES_AT = "expiresAt";
    private static final String CREATED_AT = "createdAt";
    private static final char LIKE_ESCAPE = '\\';

    private ListingSpecifications() {
    }

    /** LFP-10/15: yalnız {@code now} anında açık ilanlar (süresi geçmiş ama işaretlenmemiş ilan dahil değil). */
    public static Specification<Listing> openAt(Instant now) {
        return (root, query, cb) -> cb.and(cb.equal(root.get(STATUS), ListingStatus.OPEN),
                cb.greaterThan(root.get(EXPIRES_AT), now));
    }

    public static Specification<Listing> ownedBy(UUID ownerId) {
        return (root, query, cb) -> cb.equal(root.get("ownerId"), ownerId);
    }

    /** İlanın {@code now} anındaki durumuna göre süzgeç ({@link Listing#statusAt} ile aynı kural). */
    public static Specification<Listing> statusAt(ListingStatus status, Instant now) {
        return switch (status) {
            case OPEN -> openAt(now);
            case CLOSED -> (root, query, cb) -> cb.equal(root.get(STATUS), ListingStatus.CLOSED);
            case EXPIRED -> (root, query, cb) -> cb.or(cb.equal(root.get(STATUS), ListingStatus.EXPIRED),
                    cb.and(cb.equal(root.get(STATUS), ListingStatus.OPEN),
                            cb.lessThanOrEqualTo(root.get(EXPIRES_AT), now)));
        };
    }

    /** Keyset: sıralamada {@code (createdAt, id)}'den sonra gelen ilanlar. */
    public static Specification<Listing> after(Instant createdAt, UUID id) {
        return (root, query, cb) -> cb.or(cb.lessThan(root.get(CREATED_AT), createdAt),
                cb.and(cb.equal(root.get(CREATED_AT), createdAt), cb.lessThan(root.<UUID>get("id"), id)));
    }

    public static Specification<Listing> gameIs(UUID gameId) {
        return (root, query, cb) -> cb.equal(root.get("gameId"), gameId);
    }

    public static Specification<Listing> platformIs(Platform platform) {
        return (root, query, cb) -> cb.equal(root.get("platform"), platform);
    }

    public static Specification<Listing> micRequiredIs(boolean micRequired) {
        return (root, query, cb) -> cb.equal(root.get("micRequired"), micRequired);
    }

    /** Seviye metninde geçen ifade; seviyesi belirtilmemiş ilan her seviyeye açık sayılır ve eşleşir. */
    public static Specification<Listing> skillLevelMatches(String text) {
        return (root, query, cb) -> cb.or(cb.isNull(root.get("skillLevel")),
                containsIgnoreCase(cb, root.get("skillLevel"), text));
    }

    /** Rollerden biri {@code roleKey} ile (büyük/küçük harf duyarsız) tam eşleşir; indeks {@code lower(role)}. */
    public static Specification<Listing> hasRole(String roleKey) {
        return (root, query, cb) -> {
            Subquery<Integer> roles = query.subquery(Integer.class);
            Root<Listing> inner = roles.from(Listing.class);
            Join<Listing, String> role = inner.join("roles");
            roles.select(cb.literal(1)).where(cb.equal(inner.get("id"), root.get("id")),
                    cb.equal(cb.lower(role), roleKey));
            return cb.exists(roles);
        };
    }

    /** Planlanan başlangıç aralıkta; sınırlardan biri {@code null} ise o yönde sınır yoktur. */
    public static Specification<Listing> playTimeBetween(Instant from, Instant to) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            Expression<Instant> start = root.get("playTimeStart");
            predicates.add(cb.isNotNull(start));
            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(start, from));
            }
            if (to != null) {
                predicates.add(cb.lessThanOrEqualTo(start, to));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    /** Oyun adında ya da açıklamada geçen ifade. */
    public static Specification<Listing> textMatches(String text) {
        return (root, query, cb) -> cb.or(containsIgnoreCase(cb, root.get("gameTitle"), text),
                containsIgnoreCase(cb, root.get("description"), text));
    }

    /** Verilen süzgeçlerin hepsi (VE). Liste boşsa her ilan eşleşir. */
    public static Specification<Listing> allOf(List<Specification<Listing>> specifications) {
        return (root, query, cb) -> cb.and(specifications.stream()
                .map(specification -> specification.toPredicate(root, query, cb))
                .toArray(Predicate[]::new));
    }

    private static Predicate containsIgnoreCase(CriteriaBuilder cb, Expression<String> field, String text) {
        return cb.like(cb.lower(field), "%" + escapeLike(text.toLowerCase(Locale.ROOT)) + "%", LIKE_ESCAPE);
    }

    static String escapeLike(String text) {
        return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
