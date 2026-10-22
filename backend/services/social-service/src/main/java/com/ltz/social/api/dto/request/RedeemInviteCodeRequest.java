package com.ltz.social.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Elle yazılmış kod boşluk/tire içerebilir; biçim denetimi ve normalleştirme serviste yapılır (biçimsiz kod ile
 * bilinmeyen kod aynı 404'ü alır). Yalnız uzunluk sınırı burada uygulanır.
 */
public record RedeemInviteCodeRequest(@NotBlank @Size(max = 32) String code) {
}
