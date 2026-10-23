package com.ltz.auth.infrastructure.security;

import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.util.Base64;
import java.util.Map;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;

/**
 * Auth'un RS256 imza anahtarı. Özel anahtar yalnız bu serviste bulunur; diğer servisler yalnız JWKS'teki
 * açık anahtarı görür. kid, açık anahtarın RFC 7638 parmak izidir; anahtar değişince kendiliğinden değişir.
 * Hata mesajları anahtarın hiçbir parçasını içermez.
 */
public final class JwtSigningKey {

    static final int MIN_KEY_BITS = 3072;
    private static final String RSA = "RSA";

    private final RSAKey jwk;

    private JwtSigningKey(RSAKey jwk) {
        this.jwk = jwk;
    }

    public static JwtSigningKey fromBase64Pkcs8(String encodedKey) {
        RSAPrivateCrtKey privateKey;
        RSAPublicKey publicKey;
        try {
            KeyFactory keyFactory = KeyFactory.getInstance(RSA);
            byte[] der = Base64.getDecoder().decode(encodedKey.strip());
            if (!(keyFactory.generatePrivate(new PKCS8EncodedKeySpec(der)) instanceof RSAPrivateCrtKey crtKey)) {
                throw new IllegalStateException(invalidKeyMessage());
            }
            privateKey = crtKey;
            publicKey = (RSAPublicKey) keyFactory.generatePublic(
                    new RSAPublicKeySpec(crtKey.getModulus(), crtKey.getPublicExponent()));
        } catch (IllegalArgumentException | GeneralSecurityException _) {
            throw new IllegalStateException(invalidKeyMessage());
        }
        if (publicKey.getModulus().bitLength() < MIN_KEY_BITS) {
            throw new IllegalStateException(invalidKeyMessage());
        }
        try {
            return new JwtSigningKey(new RSAKey.Builder(publicKey)
                    .privateKey(privateKey)
                    .keyUse(KeyUse.SIGNATURE)
                    .algorithm(JWSAlgorithm.RS256)
                    .keyIDFromThumbprint()
                    .build());
        } catch (JOSEException _) {
            throw new IllegalStateException("JWT signing key thumbprint could not be computed");
        }
    }

    public RSAKey jwk() {
        return jwk;
    }

    public String keyId() {
        return jwk.getKeyID();
    }

    public RSAPublicKey publicKey() {
        try {
            return jwk.toRSAPublicKey();
        } catch (JOSEException _) {
            throw new IllegalStateException("JWT public key could not be extracted");
        }
    }

    /** Yalnız açık anahtar alanları; özel anahtar parçaları ("d", "p", "q" ...) asla yayınlanmaz. */
    public Map<String, Object> publicJwkSet() {
        return new JWKSet(jwk.toPublicJWK()).toJSONObject(true);
    }

    private static String invalidKeyMessage() {
        return "ltz.auth.jwt.private-key (AUTH_JWT_PRIVATE_KEY) must be a Base64 PKCS#8 RSA private key of at least "
                + MIN_KEY_BITS + " bits";
    }
}
