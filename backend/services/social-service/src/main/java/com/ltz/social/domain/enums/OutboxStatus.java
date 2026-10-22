package com.ltz.social.domain.enums;

/** Outbox olayının teslim durumu (DB'de varchar + CHECK). */
public enum OutboxStatus {
    PENDING,
    SENT,
    FAILED
}
