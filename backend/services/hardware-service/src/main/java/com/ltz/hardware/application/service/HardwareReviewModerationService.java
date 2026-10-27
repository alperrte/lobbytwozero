package com.ltz.hardware.application.service;

import com.ltz.hardware.api.dto.request.ModerateHardwareReviewRequest;
import com.ltz.hardware.api.dto.response.*;
import com.ltz.hardware.application.mapper.*;
import com.ltz.hardware.application.port.HardwareReviewModerationAccess;
import com.ltz.hardware.domain.entity.CatalogText;
import com.ltz.hardware.domain.enums.HardwareReviewModerationAction;
import com.ltz.hardware.domain.event.HardwareReviewEvent;
import com.ltz.hardware.domain.exception.HardwareNotFoundException;
import com.ltz.hardware.infrastructure.messaging.outbox.HardwareModerationOutbox;
import com.ltz.hardware.infrastructure.persistence.repository.*;
import java.util.Objects;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class HardwareReviewModerationService {
    private final HardwareReviewRepository reviews;
    private final HardwareReviewReportRepository reports;
    private final HardwareReviewModerationAccess access;
    private final HardwareReviewWriteRepository writes;
    private final HardwareModerationOutbox outbox;
    private final HardwareReviewMapper mapper;
    private final HardwareProductRepository products;

    public HardwareReviewModerationService(HardwareReviewRepository reviews, HardwareReviewReportRepository reports,
            HardwareReviewModerationAccess access, HardwareReviewWriteRepository writes,
            HardwareModerationOutbox outbox, HardwareReviewMapper mapper, HardwareProductRepository products) {
        this.reviews = reviews;
        this.reports = reports;
        this.access = access;
        this.writes = writes;
        this.outbox = outbox;
        this.mapper = mapper;
        this.products = products;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public HardwareReviewModerationDetailResponse detail(UUID reviewId, int page, int size) {
        var viewer = access.requireModeratorId();
        var review = reviews.findById(reviewId).orElseThrow(HardwareNotFoundException::new);
        var rows = reports.findAllByReviewId(reviewId, PageRequest.of(page, size, Sort.by("createdAt", "id")));
        var statuses = outbox.statuses(rows.stream().map(r -> r.getOutboxEventId()).filter(Objects::nonNull).toList());
        return new HardwareReviewModerationDetailResponse(mapper.toResponse(review, viewer),
                rows.stream().map(r -> HardwareReviewReportMapper.fromStatuses(r, statuses)).toList(),
                page, size, rows.getTotalElements(), rows.getTotalPages());
    }

    @Transactional
    public HardwareReviewModerationResponse apply(UUID reviewId, ModerateHardwareReviewRequest request, String peer) {
        var moderator = access.requireModeratorId();
        writes.limit(moderator, peer, true);
        var review = reviews.findForUpdate(reviewId).orElseThrow(HardwareNotFoundException::new);
        review.requireVersion(request.reviewVersion());
        var reason = CatalogText.clean(request.reason(), 500);
        if (request.action() == HardwareReviewModerationAction.RESTORE) {
            products.findActiveForSelection(review.getProductId()).orElseThrow(HardwareNotFoundException::new);
        }
        review.moderate(request.action());
        reviews.flush();
        writes.append(moderator, request.action().name() + "_REVIEW", reviewId, reason);
        var event = HardwareReviewEvent.moderated(review);
        outbox.append(event);
        return new HardwareReviewModerationResponse(mapper.toResponse(review, moderator), event.eventId(), "NOT_DELIVERED");
    }
}
