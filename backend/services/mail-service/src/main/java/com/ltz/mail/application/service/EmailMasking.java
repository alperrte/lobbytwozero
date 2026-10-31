package com.ltz.mail.application.service;

/**
 * Gönderim geçmişinde tutulan maskeli adres: {@code kerem@gmail.com → k***@g***.com}. Tam adres yalnız gönderim bitene
 * kadar saklanır (V81 değişmezi); geçmiş yalnız bu biçimi taşır.
 */
final class EmailMasking {

    private static final String MASK = "***";

    private EmailMasking() {
    }

    static String mask(String email) {
        int at = email.lastIndexOf('@');
        if (at <= 0 || at == email.length() - 1) {
            return MASK;
        }
        String local = email.substring(0, at);
        String domain = email.substring(at + 1);
        int dot = domain.lastIndexOf('.');
        String domainHead = domain.substring(0, 1) + MASK;
        String suffix = dot > 0 && dot < domain.length() - 1 ? domain.substring(dot) : "";
        return local.substring(0, 1) + MASK + "@" + domainHead + suffix;
    }
}
