package com.ltz.auth.domain.enums;

import com.ltz.auth.domain.entity.Account;

/**
 * Hesap türü; saklanmaz, kayıt yönteminden, 2FA durumundan ve bağlı sağlayıcılardan her seferinde hesaplanır.
 * <ul>
 *   <li><b>A</b>: normal kayıt, 2FA yok, bağlı Google/Steam yok. Güvencesi en düşük tür; bazı işlemlerde adım
 *       yükseltme ister.</li>
 *   <li><b>B</b>: normal kayıt + (2FA açık veya Google/Steam bağlı). Kendi başına silemez, yöneticiye talep açar.</li>
 *   <li><b>C</b>: doğrudan Google/Steam ile kayıt. Kendi başına silemez; Google ile açılan hesabın e-postası
 *       sağlayıcıya bağlıdır.</li>
 * </ul>
 */
public enum AccountTier {
    A,
    B,
    C;

    public static AccountTier of(Account.RegistrationMethod method, boolean totpEnabled, boolean hasLinkedProvider) {
        if (method.isExternal()) {
            return C;
        }
        return totpEnabled || hasLinkedProvider ? B : A;
    }

    /** B ve C güvenceli hesaptır; A için kısıtlı işlemlerde {@code STEP_UP_REQUIRED} beklenir. */
    public boolean isTrusted() {
        return this != A;
    }
}
