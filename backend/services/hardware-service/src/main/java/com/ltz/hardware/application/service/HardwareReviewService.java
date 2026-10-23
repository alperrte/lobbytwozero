package com.ltz.hardware.application.service;

import com.ltz.hardware.api.dto.request.*;
import com.ltz.hardware.api.dto.response.*;
import com.ltz.hardware.application.mapper.HardwareReviewMapper;
import com.ltz.hardware.application.port.CurrentActor;
import com.ltz.hardware.application.port.HardwareReviewAudiencePolicy;
import com.ltz.hardware.domain.entity.HardwareReview;
import com.ltz.hardware.domain.enums.*;
import com.ltz.hardware.domain.exception.HardwareNotFoundException;
import com.ltz.hardware.infrastructure.persistence.repository.*;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class HardwareReviewService {
    private final HardwareReviewRepository reviews;
    private final HardwareProductRepository products;
    private final CurrentActor actor;
    private final HardwareReviewAudiencePolicy audience;
    private final HardwareReviewSearch search;
    private final HardwareReviewMapper mapper;
    private final HardwareReviewWriteRepository writes;

    public HardwareReviewService(HardwareReviewRepository reviews, HardwareProductRepository products,
            CurrentActor actor, HardwareReviewAudiencePolicy audience, HardwareReviewSearch search,
            HardwareReviewMapper mapper, HardwareReviewWriteRepository writes) {
        this.reviews = reviews;
        this.products = products;
        this.actor = actor;
        this.audience = audience;
        this.search = search;
        this.mapper = mapper;
        this.writes = writes;
    }

    @Transactional
    public HardwareReviewResponse create(UUID productId, CreateHardwareReviewRequest request, String peer) {
        var viewer = actor.requireUserId();
        writes.limit(viewer, peer, false);
        products.findActiveForSelection(productId).orElseThrow(HardwareNotFoundException::new);
        var review = reviews.saveAndFlush(new HardwareReview(productId, viewer, request.body(), request.rating()));
        writes.append(viewer, "CREATE_REVIEW", review.getId(), "USER_REVIEW_CREATED");
        return mapper.toResponse(review, viewer);
    }

    @Transactional
    public HardwareReviewResponse update(UUID id, UpdateHardwareReviewRequest request, String peer) {
        var viewer = actor.requireUserId();
        writes.limit(viewer, peer, false);
        var review = reviews.findOwnedForUpdate(id, viewer).orElseThrow(HardwareNotFoundException::new);
        review.requireVersion(request.reviewVersion());
        products.findActiveForSelection(review.getProductId()).orElseThrow(HardwareNotFoundException::new);
        review.update(request.body(), request.rating());
        reviews.flush();
        writes.append(viewer, "UPDATE_REVIEW", id, "USER_REVIEW_UPDATED");
        return mapper.toResponse(review, viewer);
    }

    @Transactional
    public void delete(UUID id, long version, String peer) {
        var viewer = actor.requireUserId();
        writes.limit(viewer, peer, false);
        var review = reviews.findOwnedForUpdate(id, viewer).orElseThrow(HardwareNotFoundException::new);
        review.requireVersion(version);
        review.delete();
        reviews.flush();
        writes.append(viewer, "DELETE_REVIEW", id, "USER_REVIEW_DELETED");
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public HardwareReviewResponse detail(UUID id) {
        var viewer = actor.requireUserId();
        var review = reviews.findById(id).filter(r -> r.getStatus() != HardwareReviewStatus.DELETED)
                .orElseThrow(HardwareNotFoundException::new);
        if (!viewer.equals(review.getUserId())) {
            if (review.getStatus() != HardwareReviewStatus.VISIBLE || !decision(viewer).permits(viewer, review.getUserId())) {
                throw new HardwareNotFoundException();
            }
            requireActiveProduct(review.getProductId());
        }
        return mapper.toResponse(review, viewer);
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public HardwareReviewPageResponse list(UUID productId, int page, int size) {
        var viewer = actor.requireUserId();
        requireActiveProduct(productId);
        var access = decision(viewer);
        var result = reviews.findAll(search.visible(productId, viewer, access), pageable(page, size));
        return new HardwareReviewPageResponse(result.stream().map(r -> mapper.toResponse(r, viewer)).toList(),
                page, size, result.getTotalElements(), result.getTotalPages(), search.summary(productId, viewer, access));
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public HardwareOwnReviewPageResponse mine(int page, int size) {
        var viewer = actor.requireUserId();
        var result = reviews.findAllByUserIdAndStatusNot(viewer, HardwareReviewStatus.DELETED, pageable(page, size));
        return new HardwareOwnReviewPageResponse(result.stream().map(r -> mapper.toResponse(r, viewer)).toList(),
                page, size, result.getTotalElements(), result.getTotalPages());
    }

    private void requireActiveProduct(UUID productId) {
        products.findByIdAndStatus(productId, CatalogStatus.ACTIVE).orElseThrow(HardwareNotFoundException::new);
    }

    private PageRequest pageable(int page, int size) { return PageRequest.of(page, size, Sort.by("createdAt", "id")); }

    public HardwareReviewAudiencePolicy.Decision decision(UUID viewer) {
        try {
            var result = audience.evaluate(viewer);
            return result == null ? HardwareReviewAudiencePolicy.Decision.denyOthers() : result;
        } catch (RuntimeException _) {
            return HardwareReviewAudiencePolicy.Decision.denyOthers();
        }
    }
}
