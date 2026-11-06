package com.ltz.auth.api.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * "Ben kimim" yanıtı. Yalnız doğrulanmış access token'ın zaten taşıdığı bilgiyi döner: e-posta, kullanıcı adı, hesap
 * durumu, oturum kimliği ve token sürümü içermez. Profil bilgisi User Service'tedir; roller yalnız arayüzü (menü vb.)
 * şekillendirmek içindir, yetki kararı her serviste token'dan yeniden verilir.
 */
public record CurrentAccountResponse(UUID accountId, List<String> roles, List<String> authMethods,
                                     Instant accessTokenExpiresAt) {
}
