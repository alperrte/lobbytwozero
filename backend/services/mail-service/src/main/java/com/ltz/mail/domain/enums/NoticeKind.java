package com.ltz.mail.domain.enums;

/** Bilgilendirme türü; e-postanın renk dili ve üstündeki etiketi belirler. */
public enum NoticeKind {
    INFO("accent", "BİLGİLENDİRME", "INFORMATION"),
    SUCCESS("success", "BAŞARILI", "SUCCESS"),
    WARNING("warn", "UYARI", "WARNING"),
    DANGER("danger", "ÖNEMLİ", "IMPORTANT");

    private final String color;
    private final String turkishLabel;
    private final String englishLabel;

    NoticeKind(String color, String turkishLabel, String englishLabel) {
        this.color = color;
        this.turkishLabel = turkishLabel;
        this.englishLabel = englishLabel;
    }

    /** {@code MailBlocks} renk anahtarı: accent, warn, danger, success. */
    public String color() {
        return color;
    }

    public String label(boolean turkish) {
        return turkish ? turkishLabel : englishLabel;
    }
}
