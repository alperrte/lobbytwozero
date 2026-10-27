package com.ltz.auth.application.port;

import java.util.Map;

/** Steam OpenID 2.0 check_authentication: yanıt imzasını Steam'e doğrulatır. */
public interface SteamAssertionVerifier {

    /**
     * @param openIdParams istemciden gelen tüm openid.* alanları (olduğu gibi)
     * @return Steam "is_valid:true" döndürdüyse true
     * @throws ExternalAuthenticationException Steam erişilemez veya yanıt anlaşılamaz
     */
    boolean isValid(Map<String, String> openIdParams);
}
