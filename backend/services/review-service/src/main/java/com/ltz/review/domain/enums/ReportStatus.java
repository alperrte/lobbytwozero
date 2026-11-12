package com.ltz.review.domain.enums;

/** Rapor durumu (review_reports.status). Açık rapor RECEIVED'dır; kapanan rapor yeniden açılmaz. */
public enum ReportStatus {
    RECEIVED,
    REVIEWED,
    DISMISSED
}
