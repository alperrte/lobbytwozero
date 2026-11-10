package com.ltz.mail.api.dto.request;

import java.util.UUID;

import com.ltz.mail.domain.enums.NoticeKind;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Platform kapısı (Auth dışındaki kayıtlı servisler → Mail): kullanıcıya bilgilendirme maili. Servis alıcının adresini
 * VERMEZ, yalnız {@code accountId} verir; adres gönderim anında Auth'tan çözülür. Tüm metinler DÜZ METİNDİR (HTML
 * yazılmaz, kaçışlama Mail'dedir). {@code actionLabel} ve {@code actionLink} birlikte verilir ya da hiç verilmez.
 * {@code locale} boşsa {@code tr}: içeriği çağıran kendi yerelleştirir, Mail çevirmez.
 */
public record NoticeMailRequest(
        @NotNull UUID idempotencyKey,
        @NotNull UUID accountId,
        @NotNull NoticeKind kind,
        @NotBlank @Size(max = 80) String title,
        @NotBlank @Size(max = 600) String message,
        @Size(max = 30) String actionLabel,
        @Size(max = 2048) String actionLink,
        @Pattern(regexp = "^(tr|en)$") String locale) {

    @Override
    public String toString() {
        return "NoticeMailRequest[idempotencyKey=" + idempotencyKey + ", kind=" + kind + ", content=***]";
    }
}
