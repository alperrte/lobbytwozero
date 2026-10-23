package com.ltz.hardware.application.service;

import com.ltz.hardware.api.dto.request.CreateHardwareReviewReportRequest;
import com.ltz.hardware.api.dto.response.HardwareReviewReportResponse;
import com.ltz.hardware.application.mapper.HardwareReviewReportMapper;
import com.ltz.hardware.application.port.CurrentActor;
import com.ltz.hardware.domain.entity.HardwareReviewReport;
import com.ltz.hardware.domain.enums.*;
import com.ltz.hardware.domain.event.HardwareReviewEvent;
import com.ltz.hardware.domain.exception.*;
import com.ltz.hardware.infrastructure.messaging.outbox.HardwareModerationOutbox;
import com.ltz.hardware.infrastructure.persistence.repository.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class HardwareReviewReportService {
    private final HardwareReviewRepository reviews;
    private final HardwareProductRepository products;
    private final HardwareReviewReportRepository reports;
    private final CurrentActor actor;
    private final HardwareReviewService reviewService;
    private final HardwareReviewWriteRepository writes;
    private final HardwareModerationOutbox outbox;

    public HardwareReviewReportService(HardwareReviewRepository reviews, HardwareProductRepository products,
            HardwareReviewReportRepository reports, CurrentActor actor, HardwareReviewService reviewService,
            HardwareReviewWriteRepository writes, HardwareModerationOutbox outbox) {
        this.reviews = reviews;
        this.products = products;
        this.reports = reports;
        this.actor = actor;
        this.reviewService = reviewService;
        this.writes = writes;
        this.outbox = outbox;
    }

    @Transactional
    public HardwareReviewReportResponse create(UUID reviewId, CreateHardwareReviewReportRequest request, String peer) {
        var viewer = actor.requireUserId();
        writes.limit(viewer, peer, false);
        var review = reviews.findForUpdate(reviewId).orElseThrow(HardwareNotFoundException::new);
        if (viewer.equals(review.getUserId())) throw new IllegalArgumentException("Kendi yorumunuzu raporlayamazsınız");
        if (review.getStatus() != HardwareReviewStatus.VISIBLE || !reviewService.decision(viewer).permits(viewer, review.getUserId())) {
            throw new HardwareNotFoundException();
        }
        products.findActiveForSelection(review.getProductId()).orElseThrow(HardwareNotFoundException::new);
        if (reports.existsByReviewIdAndReporterId(reviewId, viewer)) throw new HardwareConflictException();
        var reportId = UUID.randomUUID();
        var event = HardwareReviewEvent.reported(review, reportId, request.reason());
        outbox.append(event);
        var report = reports.saveAndFlush(new HardwareReviewReport(reportId, reviewId, viewer,
                request.reason(), request.details(), event.eventId()));
        writes.append(viewer, "REPORT_REVIEW", reviewId, "USER_CONTENT_REPORTED");
        return HardwareReviewReportMapper.toResponse(report, "PENDING");
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public HardwareReviewReportResponse get(UUID id) {
        var report = reports.findByIdAndReporterId(id, actor.requireUserId()).orElseThrow(HardwareNotFoundException::new);
        var ids = report.getOutboxEventId() == null ? List.<UUID>of() : List.of(report.getOutboxEventId());
        return HardwareReviewReportMapper.fromStatuses(report, outbox.statuses(ids));
    }
}
