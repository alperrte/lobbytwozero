package com.ltz.auth.api.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.ltz.auth.domain.entity.Account;
import com.ltz.auth.domain.enums.AccountTier;

/**
 * "Ben kimim" yanıtı. Token'ın taşıdığı kimlik/rol bilgisine hesabın güvence görünümünü ekler (parola var mı, bağlı
 * sağlayıcılar, TOTP, kayıt yöntemi, hesap türü A/B/C); e-posta, kullanıcı adı, hesap durumu, oturum kimliği ve token
 * sürümü içermez. {@code adultVerified} yaş doğrulamasının veritabanındaki güncel sonucudur (token iddiasından ≤60 sn
 * önce değişebilir); doğum tarihi, yaş ya da doğrulama zamanı içermez. Profil bilgisi User Service'tedir; roller yalnız arayüzü (menü vb.)
 * şekillendirmek içindir, yetki kararı her serviste token'dan yeniden verilir.
 */
public record CurrentAccountResponse(UUID accountId, List<String> roles, List<String> authMethods,
                                     Instant accessTokenExpiresAt, boolean passwordSet, List<String> providers,
                                     boolean totpEnabled, Account.RegistrationMethod registrationMethod,
                                     AccountTier accountTier, boolean trusted, boolean adultVerified) {

    public CurrentAccountResponse {
        roles = List.copyOf(roles);
        authMethods = List.copyOf(authMethods);
        providers = List.copyOf(providers);
    }
}
