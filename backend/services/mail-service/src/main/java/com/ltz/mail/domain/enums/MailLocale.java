package com.ltz.mail.domain.enums;

/** Desteklenen e-posta dilleri (MAIL-09). Dil belirtilmezse e-posta iki dilli (TR + EN) gönderilir. */
public enum MailLocale {
    TR("tr"),
    EN("en");

    private final String code;

    MailLocale(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    /** @return {@code null} dil belirtilmediği anlamına gelir (iki dilli) */
    public static MailLocale fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (MailLocale locale : values()) {
            if (locale.code.equals(code)) {
                return locale;
            }
        }
        throw new IllegalArgumentException("Unsupported mail locale");
    }
}
