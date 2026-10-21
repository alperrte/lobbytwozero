package com.ltz.auth.domain.enums;

public enum SteamProofPurpose {
    /** Doğrulanmış SteamID ile yeni hesap açma hakkı. */
    REGISTRATION,
    /** Bekleyen Steam hesabının e-posta doğrulamasını yapanın SteamID'yi kontrol ettiğinin kanıtı. */
    EMAIL_VERIFICATION
}
