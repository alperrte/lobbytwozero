package com.ltz.auth.infrastructure.security;

import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
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
    /** JWKS'te yayınlanan, yalnız doğrulama için ek açık anahtarlar (rotasyon geçişi). */
    private final List<RSAKey> additionalPublicKeys;

    private JwtSigningKey(RSAKey jwk, List<RSAKey> additionalPublicKeys) {
        this.jwk = jwk;
        this.additionalPublicKeys = additionalPublicKeys;
    }

    public static JwtSigningKey fromBase64Pkcs8(String encodedKey) {
        return fromBase64Pkcs8(encodedKey, List.of());
    }

    public static JwtSigningKey fromBase64Pkcs8(String encodedKey, List<String> additionalPublicKeys) {
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
            RSAKey signing = new RSAKey.Builder(publicKey)
                    .privateKey(privateKey)
                    .keyUse(KeyUse.SIGNATURE)
                    .algorithm(JWSAlgorithm.RS256)
                    .keyIDFromThumbprint()
                    .build();
            return new JwtSigningKey(signing, parseAdditional(signing.getKeyID(), additionalPublicKeys));
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

    /**
     * Yalnız açık anahtar alanları; özel anahtar parçaları ("d", "p", "q" ...) asla yayınlanmaz. İmza anahtarı ilk sıradadır,
     * ek doğrulama anahtarları (varsa) ardından gelir.
     */
    public Map<String, Object> publicJwkSet() {
        List<com.nimbusds.jose.jwk.JWK> keys = new ArrayList<>();
        keys.add(jwk.toPublicJWK());
        keys.addAll(additionalPublicKeys);
        return new JWKSet(keys).toJSONObject(true);
    }

    /** Ek anahtarlar X.509 açık anahtarıdır (özel anahtar kabul edilmez); imza anahtarıyla aynı kid yinelenmez. */
    private static List<RSAKey> parseAdditional(String signingKeyId, List<String> encodedKeys) {
        Map<String, RSAKey> byKeyId = new LinkedHashMap<>();
        for (String encoded : encodedKeys) {
            RSAKey key = parsePublicKey(encoded);
            if (!key.getKeyID().equals(signingKeyId)) {
                byKeyId.putIfAbsent(key.getKeyID(), key);
            }
        }
        return List.copyOf(byKeyId.values());
    }

    private static RSAKey parsePublicKey(String encoded) {
        try {
            byte[] der = Base64.getDecoder().decode(encoded.strip());
            RSAPublicKey publicKey = (RSAPublicKey) KeyFactory.getInstance(RSA).generatePublic(new X509EncodedKeySpec(der));
            if (publicKey.getModulus().bitLength() < MIN_KEY_BITS) {
                throw new IllegalStateException(invalidAdditionalKeyMessage());
            }
            return new RSAKey.Builder(publicKey)
                    .keyUse(KeyUse.SIGNATURE)
                    .algorithm(JWSAlgorithm.RS256)
                    .keyIDFromThumbprint()
                    .build();
        } catch (ClassCastException | IllegalArgumentException | GeneralSecurityException | JOSEException _) {
            throw new IllegalStateException(invalidAdditionalKeyMessage());
        }
    }

    private static String invalidAdditionalKeyMessage() {
        return "ltz.auth.jwt.additional-public-keys entries must be Base64 X.509 RSA public keys of at least "
                + MIN_KEY_BITS + " bits";
    }

    private static String invalidKeyMessage() {
        return "ltz.auth.jwt.private-key (AUTH_JWT_PRIVATE_KEY) must be a Base64 PKCS#8 RSA private key of at least "
                + MIN_KEY_BITS + " bits";
    }
}
