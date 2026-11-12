package com.ltz.review.domain.enums;

/** Kullanıcı başına kota uygulanan mutasyonlar; değer review_rate_limits.action sütununa yazılır. */
public enum AbuseAction {
    REVIEW_CREATE,
    /** Beğenme ve beğeniyi kaldırma aynı kotayı paylaşır. */
    REVIEW_LIKE,
    /** İnceleme raporu oluşturma (REV-07/REV-13); tekrar ve mükerrer denemeler de sayılır. */
    REPORT_CREATE
}
