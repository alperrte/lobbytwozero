package com.ltz.lfp.application.service;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ltz.lfp.application.mapper.ListingMapper;
import com.ltz.lfp.domain.entity.Listing;
import com.ltz.lfp.domain.enums.ListingStatus;
import com.ltz.lfp.domain.exception.LfpErrorCode;
import com.ltz.lfp.domain.exception.LfpException;
import com.ltz.lfp.infrastructure.config.ListingProperties;
import com.ltz.lfp.infrastructure.persistence.repository.ListingRepository;
import com.ltz.lfp.infrastructure.persistence.repository.ListingSpecifications;

import lombok.RequiredArgsConstructor;

/**
 * İlan okumaları (LTZ-UMT-LFP-02): aktif ilan listesi ve arama (LFP-10, LFP-11), sahibin kendi ilanları ve ilan
 * detayı. Aktif liste yalnız okuma anında açık ilanları içerir (LFP-15: süresi geçen ilan zamanlayıcıyı beklemeden
 * listeden çıkar). Sayfalar en yeni önce, keyset imleciyle ilerler; sayfa başına sabit sayıda sorgu çalışır (roller
 * toplu yüklenir).
 */
@Service
@RequiredArgsConstructor
public class ListingQueryService {

    private final ListingRepository listings;
    private final ListingProperties properties;
    private final Clock clock;

    /** LFP-10/11: aktif ilanlar, isteğe bağlı süzgeçlerle. */
    @Transactional(readOnly = true)
    public ListingPageView search(ListingSearch search, Integer limit, String cursor) {
        ListingSearch filters = search.normalized();
        int size = pageSize(limit);
        Instant now = now();
        List<Specification<Listing>> specifications = new ArrayList<>();
        specifications.add(ListingSpecifications.openAt(now));
        addFilters(specifications, filters);
        return page(specifications, size, cursor, now);
    }

    /** Sahibin tüm ilanları (açık, kapalı, süresi dolmuş); isteğe bağlı okuma anındaki duruma göre süzgeç. */
    @Transactional(readOnly = true)
    public ListingPageView mine(UUID ownerId, ListingStatus status, Integer limit, String cursor) {
        int size = pageSize(limit);
        Instant now = now();
        List<Specification<Listing>> specifications = new ArrayList<>();
        specifications.add(ListingSpecifications.ownedBy(ownerId));
        if (status != null) {
            specifications.add(ListingSpecifications.statusAt(status, now));
        }
        return page(specifications, size, cursor, now);
    }

    /** İlan detayı; her durumdaki ilan görülebilir (durum yanıtta). Yoksa 404. */
    @Transactional(readOnly = true)
    public ListingView get(UUID listingId) {
        Listing listing = listings.findById(listingId)
                .orElseThrow(() -> new LfpException(LfpErrorCode.LISTING_NOT_FOUND));
        return ListingMapper.toView(listing, now());
    }

    private static void addFilters(List<Specification<Listing>> specifications, ListingSearch filters) {
        if (filters.gameId() != null) {
            specifications.add(ListingSpecifications.gameIs(filters.gameId()));
        }
        if (filters.platform() != null) {
            specifications.add(ListingSpecifications.platformIs(filters.platform()));
        }
        if (filters.micRequired() != null) {
            specifications.add(ListingSpecifications.micRequiredIs(filters.micRequired()));
        }
        if (filters.skillLevel() != null) {
            specifications.add(ListingSpecifications.skillLevelMatches(filters.skillLevel()));
        }
        if (filters.role() != null) {
            specifications.add(ListingSpecifications.hasRole(filters.role()));
        }
        if (filters.playFrom() != null || filters.playTo() != null) {
            specifications.add(ListingSpecifications.playTimeBetween(filters.playFrom(), filters.playTo()));
        }
        if (filters.query() != null) {
            specifications.add(ListingSpecifications.textMatches(filters.query()));
        }
    }

    private ListingPageView page(List<Specification<Listing>> specifications, int size, String cursor, Instant now) {
        if (cursor != null) {
            KeysetCursor after = KeysetCursor.decode(cursor);
            specifications.add(ListingSpecifications.after(after.createdAt(), after.id()));
        }
        List<Listing> rows = listings.findBy(ListingSpecifications.allOf(specifications),
                query -> query.sortBy(ListingSpecifications.NEWEST_FIRST).limit(size + 1).all());
        boolean hasMore = rows.size() > size;
        List<ListingView> items = rows.stream().limit(size).map(listing -> ListingMapper.toView(listing, now)).toList();
        String nextCursor = hasMore
                ? new KeysetCursor(items.getLast().createdAt(), items.getLast().id()).encode()
                : null;
        return new ListingPageView(items, nextCursor);
    }

    /** Verilmezse varsayılan; aralık dışı 400 {@code INVALID_PAGE_SIZE}. */
    private int pageSize(Integer requested) {
        if (requested == null) {
            return properties.defaultPageSize();
        }
        if (requested < 1 || requested > properties.maxPageSize()) {
            throw new LfpException(LfpErrorCode.INVALID_PAGE_SIZE);
        }
        return requested;
    }

    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }
}
