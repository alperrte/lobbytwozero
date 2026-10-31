package com.ltz.auth.api.dto.response;

/** Bağlama başlatma yanıtı: tarayıcının yönlendirileceği Steam OpenID adresi (state ayrıca HttpOnly çerezdedir). */
public record ExternalLinkStartResponse(String redirectUrl) {
}
