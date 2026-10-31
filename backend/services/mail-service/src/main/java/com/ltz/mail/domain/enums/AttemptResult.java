package com.ltz.mail.domain.enums;

/** Tek bir gönderim denemesinin sonucu (MAIL-15). */
public enum AttemptResult {
    SENT,
    TRANSIENT_FAILURE,
    PERMANENT_FAILURE,
    EXPIRED
}
