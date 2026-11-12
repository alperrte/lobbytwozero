package com.ltz.review.domain.enums;

/**
 * Rapor gerekçesi (kapalı liste). Geçici sözlüktür: Admin Service'in nihai gerekçe sözlüğü kesinleşince birlikte
 * güncellenir; değerler review_reports.reason CHECK kısıtıyla aynıdır.
 */
public enum ReportReason {
    SPAM,
    HARASSMENT,
    HATE_SPEECH,
    PERSONAL_INFO,
    SPOILER,
    OFF_TOPIC,
    OTHER
}
