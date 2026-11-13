package com.ltz.lfp.application.service;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.ltz.lfp.application.mapper.ListingMapper;
import com.ltz.lfp.domain.entity.Listing;
import com.ltz.lfp.domain.enums.ListingStatus;
import com.ltz.lfp.domain.exception.LfpErrorCode;
import com.ltz.lfp.domain.exception.LfpException;
import com.ltz.lfp.infrastructure.config.ListingProperties;
import com.ltz.lfp.infrastructure.persistence.repository.ListingRepository;
import com.ltz.lfp.infrastructure.persistence.repository.ListingSpecifications;

/**
 * İlan okumaları: aktif ilan listesi ve arama (LFP-10, LFP-11), sahibin kendi ilanları, ilan detayı ve ilan sahibiyle
 * iletişim izni (LFP-16). Engel kuralı (LFP-17) social-service'e sorulur: aralarında engel olan kullanıcıların ilanları
 * listede görünmez, detayı ve iletişimi {@code 404}'tür (ilanın varlığı sızdırılmaz). Veritabanı okumaları kısa, salt
 * okunur transaction'lardadır; Social çağrısı transaction dışındadır (uzak çağrı süresince bağlantı tutulmaz).
 */
@Service
public class ListingQueryService {

    /** Engellenen sahipler atlandığında sayfayı doldurmak için en fazla bu kadar parti okunur. */
    static final int MAX_FILL_ROUNDS = 3;

    private final ListingRepository listings;
    private final ListingProperties properties;
    private final BlockGuard blockGuard;
    private final TransactionTemplate readOnly;
    private final Clock clock;

    public ListingQueryService(ListingRepository listings, ListingProperties properties, BlockGuard blockGuard,
                               PlatformTransactionManager transactionManager, Clock clock) {
        this.listings = listings;
        this.properties = properties;
        this.blockGuard = blockGuard;
        this.readOnly = new TransactionTemplate(transactionManager);
        this.readOnly.setReadOnly(true);
        this.clock = clock;
    }

    /**
     * LFP-10/11/17: aktif ilanlar, isteğe bağlı süzgeçlerle; engelli sahiplerin ilanları atlanır. Sayfa bu yüzden
     * {@code limit}'ten kısa olabilir; {@code nextCursor} doluysa devamı vardır.
     */
    public ListingPageView search(UUID viewerId, ListingSearch search, Integer limit, String cursor) {
        ListingSearch filters = search.normalized();
        int size = pageSize(limit);
        KeysetCursor after = cursor == null ? null : KeysetCursor.decode(cursor);
        Instant now = now();
        List<Specification<Listing>> base = new ArrayList<>();
        base.add(ListingSpecifications.openAt(now));
        addFilters(base, filters);

        List<ListingView> items = new ArrayList<>();
        boolean more = true;
        for (int round = 0; round < MAX_FILL_ROUNDS && more && items.size() < size; round++) {
            Batch batch = fetch(base, after, size, now);
            Set<UUID> blocked = blockGuard.blockedOwners(viewerId,
                    batch.items().stream().map(ListingView::ownerId).toList());
            Fill fill = fill(items, batch.items(), blocked, size);
            after = fill.last() == null ? after : fill.last();
            more = batch.hasMore() || fill.leftOver();
        }
        return new ListingPageView(items, more && after != null ? after.encode() : null);
    }

    /** Sahibin tüm ilanları (açık, kapalı, süresi dolmuş); isteğe bağlı okuma anındaki duruma göre süzgeç. */
    public ListingPageView mine(UUID ownerId, ListingStatus status, Integer limit, String cursor) {
        int size = pageSize(limit);
        KeysetCursor after = cursor == null ? null : KeysetCursor.decode(cursor);
        Instant now = now();
        List<Specification<Listing>> base = new ArrayList<>();
        base.add(ListingSpecifications.ownedBy(ownerId));
        if (status != null) {
            base.add(ListingSpecifications.statusAt(status, now));
        }
        Batch batch = fetch(base, after, size, now);
        String nextCursor = batch.hasMore()
                ? new KeysetCursor(batch.items().getLast().createdAt(), batch.items().getLast().id()).encode()
                : null;
        return new ListingPageView(batch.items(), nextCursor);
    }

    /** İlan detayı; her durumdaki ilan görülebilir. Yoksa ya da sahibiyle aralarında engel varsa 404. */
    public ListingView get(UUID viewerId, UUID listingId) {
        ListingView listing = load(listingId);
        if (!listing.ownerId().equals(viewerId) && blockGuard.isBlocked(viewerId, listing.ownerId())) {
            throw new LfpException(LfpErrorCode.LISTING_NOT_FOUND);
        }
        return listing;
    }

    /**
     * LFP-16: ilan sahibiyle iletişim izni. Sıra: ilan var mı (404) → kendi ilanı mı (400) → aralarında engel var mı
     * (404, varlık sızdırılmaz) → ilan açık mı (409). İzin verilirse istemci messaging-service'te birebir sohbet açar;
     * messaging de engeli ayrıca denetler.
     */
    public ContactView contact(UUID viewerId, UUID listingId) {
        ListingView listing = load(listingId);
        if (listing.ownerId().equals(viewerId)) {
            throw new LfpException(LfpErrorCode.CANNOT_CONTACT_SELF);
        }
        if (blockGuard.isBlocked(viewerId, listing.ownerId())) {
            throw new LfpException(LfpErrorCode.LISTING_NOT_FOUND);
        }
        if (listing.status() != ListingStatus.OPEN) {
            throw new LfpException(LfpErrorCode.LISTING_NOT_OPEN);
        }
        return new ContactView(listing.id(), listing.ownerId());
    }

    private ListingView load(UUID listingId) {
        Instant now = now();
        return readOnly.execute(status -> listings.findById(listingId)
                .map(listing -> ListingMapper.toView(listing, now))
                .orElseThrow(() -> new LfpException(LfpErrorCode.LISTING_NOT_FOUND)));
    }

    /** Bir parti: en çok {@code size} ilan (roller transaction içinde yüklenir) ve sonrasında ilan olup olmadığı. */
    private Batch fetch(List<Specification<Listing>> base, KeysetCursor after, int size, Instant now) {
        List<Specification<Listing>> specifications = new ArrayList<>(base);
        if (after != null) {
            specifications.add(ListingSpecifications.after(after.createdAt(), after.id()));
        }
        return readOnly.execute(status -> {
            List<Listing> rows = listings.findBy(ListingSpecifications.allOf(specifications),
                    query -> query.sortBy(ListingSpecifications.NEWEST_FIRST).limit(size + 1).all());
            List<ListingView> views = rows.stream().limit(size).map(listing -> ListingMapper.toView(listing, now))
                    .toList();
            return new Batch(views, rows.size() > size);
        });
    }

    /**
     * Partideki ilanları sırayla inceler, engelli olmayanları sayfaya ekler; sayfa dolunca durur.
     *
     * @return son incelenen ilanın imleci ve partide incelenmemiş ilan kalıp kalmadığı
     */
    private static Fill fill(List<ListingView> page, List<ListingView> candidates, Set<UUID> blocked, int size) {
        KeysetCursor last = null;
        int examined = 0;
        for (ListingView candidate : candidates) {
            if (page.size() >= size) {
                break;
            }
            examined++;
            last = new KeysetCursor(candidate.createdAt(), candidate.id());
            if (!blocked.contains(candidate.ownerId())) {
                page.add(candidate);
            }
        }
        return new Fill(last, examined < candidates.size());
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

    private record Batch(List<ListingView> items, boolean hasMore) {
    }

    private record Fill(KeysetCursor last, boolean leftOver) {
    }
}
