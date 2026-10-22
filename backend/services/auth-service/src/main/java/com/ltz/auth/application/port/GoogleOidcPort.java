package com.ltz.auth.application.port;

/** Google OIDC: yetkilendirme kodunu (PKCE verifier ile) token'a çevirir ve id_token'ı tam doğrular. */
public interface GoogleOidcPort {

    /** @throws ExternalAuthenticationException token alınamadı veya id_token doğrulanamadı */
    GoogleIdentity authenticate(String code, String codeVerifier, String redirectUri, String expectedNonce);

    record GoogleIdentity(String subject, String email, boolean emailVerified) {

        @Override
        public String toString() {
            return "GoogleIdentity[subject=***, email=***, emailVerified=" + emailVerified + "]";
        }
    }
}
