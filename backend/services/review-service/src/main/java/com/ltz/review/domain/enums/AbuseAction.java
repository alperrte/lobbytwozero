package com.ltz.review.domain.enums;

/** Kullanıcı başına kota uygulanan mutasyonlar; değer review_rate_limits.action sütununa yazılır. */
public enum AbuseAction {
    REVIEW_CREATE,
    /** Beğenme ve beğeniyi kaldırma aynı kotayı paylaşır. */
    REVIEW_LIKE
}
