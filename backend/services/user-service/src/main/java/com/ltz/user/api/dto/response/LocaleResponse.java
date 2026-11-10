package com.ltz.user.api.dto.response;

/** Kullanıcının kayıtlı arayüz dili ({@code tr} ya da {@code en}). Başka hiçbir tercih bu yanıta girmez. */
public record LocaleResponse(String locale) {
}
