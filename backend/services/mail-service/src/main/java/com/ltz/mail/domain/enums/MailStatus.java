package com.ltz.mail.domain.enums;

/** E-posta kaydının yaşam döngüsü (docs/architecture.md §8): PENDING → PROCESSING → SENT, geçici hatada PROCESSING → PENDING, kalıcı hatada FAILED. */
public enum MailStatus {
    PENDING,
    PROCESSING,
    SENT,
    FAILED;

    /** Terminal durumda tam alıcı adresi ve şifreli içerik tutulmaz (V81 CHECK kısıtı). */
    public boolean isTerminal() {
        return this == SENT || this == FAILED;
    }
}
