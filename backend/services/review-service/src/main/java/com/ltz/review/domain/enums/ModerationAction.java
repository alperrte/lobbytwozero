package com.ltz.review.domain.enums;

/** Moderatörün bir incelemeye uygulayabileceği eylemler. HIDE geri alınabilir, ARCHIVE kalıcıdır (geri alınamaz). */
public enum ModerationAction {
    HIDE,
    ARCHIVE,
    RESTORE
}
