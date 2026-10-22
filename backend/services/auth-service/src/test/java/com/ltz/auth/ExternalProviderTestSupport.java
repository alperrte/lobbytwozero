package com.ltz.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.util.UriComponentsBuilder;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.PlainJWT;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import jakarta.servlet.http.Cookie;

/**
 * Google ve Steam'in yerine geçen yerel sahte sağlayıcı sunucusu. Üretim kodu aynı HTTP yoluyla çalışır; yalnız
 * adresler yapılandırmadan test sunucusuna yönlenir. Test anahtarları ve istemci bilgileri sahtedir.
 */
public abstract class ExternalProviderTestSupport extends IntegrationTestSupport {

    protected static final String GOOGLE_CLIENT_ID = "test-client-id.apps.example.test";
    protected static final String GOOGLE_CLIENT_SECRET = "test-client-secret";
    protected static final String GOOGLE_ISSUER = "https://accounts.google.com";
    protected static final String OAUTH_STATE_COOKIE = "__Host-ltz_oauth";
    protected static final String PUBLIC_BASE = "http://localhost:5080";
    protected static final String GOOGLE_KEY_ID = "test-google-key";
    private static final int GOOGLE_KEY_BITS = 2048;

    protected static final FakeProviders PROVIDERS = new FakeProviders();

    @DynamicPropertySource
    static void providerProperties(DynamicPropertyRegistry registry) {
        registry.add("ltz.auth.oauth.google.client-id", () -> GOOGLE_CLIENT_ID);
        registry.add("ltz.auth.oauth.google.client-secret", () -> GOOGLE_CLIENT_SECRET);
        registry.add("ltz.auth.oauth.google.token-uri", () -> PROVIDERS.url("/google/token"));
        registry.add("ltz.auth.oauth.google.jwks-uri", () -> PROVIDERS.url("/google/jwks"));
        registry.add("ltz.auth.oauth.steam.open-id-endpoint", () -> PROVIDERS.url("/steam/openid"));
    }

    protected record Started(String state, URI location, Cookie stateCookie) {
    }

    protected Started start(String path) throws Exception {
        MvcResult result = mockMvc.perform(get(path)).andReturn();
        URI location = URI.create(result.getResponse().getHeader("Location"));
        String state = result.getResponse().getCookie(OAUTH_STATE_COOKIE).getValue();
        return new Started(state, location, result.getResponse().getCookie(OAUTH_STATE_COOKIE));
    }

    protected static Map<String, String> query(URI uri) {
        return UriComponentsBuilder.fromUri(uri).build().getQueryParams().toSingleValueMap().entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey,
                        entry -> URLDecoder.decode(entry.getValue(), StandardCharsets.UTF_8)));
    }

    /** Callback yanıtının yönlendirdiği frontend fragment'i (ör. "status=authenticated" veya "error=KOD"). */
    protected static String fragment(MvcResult result) {
        return URI.create(result.getResponse().getHeader("Location")).getFragment();
    }

    protected static String googleIdToken(String subject, String email, boolean emailVerified, String nonce) throws Exception {
        return googleIdToken(PROVIDERS.googleKey, subject, email, emailVerified, nonce, GOOGLE_ISSUER,
                GOOGLE_CLIENT_ID, 300);
    }

    protected static String googleIdToken(KeyPair signer, String subject, String email, boolean emailVerified,
                                          String nonce, String issuer, String audience, long validForSeconds)
            throws Exception {
        long now = System.currentTimeMillis();
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer(issuer).audience(audience).subject(subject)
                .claim("email", email).claim("email_verified", emailVerified).claim("nonce", nonce)
                .issueTime(new Date(now - 60_000)).expirationTime(new Date(now + validForSeconds * 1000)).build();
        SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(GOOGLE_KEY_ID)
                .type(JOSEObjectType.JWT).build(), claims);
        jwt.sign(new RSASSASigner((RSAPrivateKey) signer.getPrivate()));
        return jwt.serialize();
    }

    protected static String unsignedIdToken(String subject, String nonce) {
        return new PlainJWT(new JWTClaimsSet.Builder().issuer(GOOGLE_ISSUER).audience(GOOGLE_CLIENT_ID)
                .subject(subject).claim("nonce", nonce).expirationTime(new Date(System.currentTimeMillis() + 300_000))
                .build()).serialize();
    }

    protected static KeyPair signingKey() {
        return PROVIDERS.googleKey;
    }

    protected static KeyPair newKeyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(GOOGLE_KEY_BITS);
        return generator.generateKeyPair();
    }

    /** Sağlayıcı uç noktaları. Her test kendi kod/id_token eşlemesini ekler; çağrılar sonradan incelenebilir. */
    protected static final class FakeProviders {

        public final KeyPair googleKey;
        public final HttpServer server;
        /** authorization code → token yanıtı gövdesi (id_token); eşzamanlı akışlar birbirine karışmaz. */
        public final Map<String, String> idTokensByCode = new ConcurrentHashMap<>();
        public final Map<String, Map<String, String>> tokenRequestsByCode = new ConcurrentHashMap<>();
        public final List<Map<String, String>> steamVerifications = new CopyOnWriteArrayList<>();
        public volatile boolean steamValid = true;
        public volatile boolean tokenEndpointFails;

        FakeProviders() {
            try {
                googleKey = newKeyPair();
                server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
            } catch (Exception exception) {
                throw new IllegalStateException(exception);
            }
            server.createContext("/google/token", this::googleToken);
            server.createContext("/google/jwks", this::googleJwks);
            server.createContext("/steam/openid", this::steamVerify);
            server.start();
        }

        public String url(String path) {
            return "http://127.0.0.1:" + server.getAddress().getPort() + path;
        }

        public void reset() {
            idTokensByCode.clear();
            tokenRequestsByCode.clear();
            steamVerifications.clear();
            steamValid = true;
            tokenEndpointFails = false;
        }

        private void googleToken(HttpExchange exchange) throws IOException {
            Map<String, String> form = form(exchange);
            tokenRequestsByCode.put(form.getOrDefault("code", ""), form);
            String idToken = idTokensByCode.get(form.get("code"));
            if (tokenEndpointFails || idToken == null) {
                respond(exchange, 400, "{\"error\":\"invalid_grant\"}");
                return;
            }
            respond(exchange, 200, "{\"access_token\":\"opaque\",\"token_type\":\"Bearer\",\"id_token\":\"" + idToken + "\"}");
        }

        private void googleJwks(HttpExchange exchange) throws IOException {
            RSAKey jwk = new RSAKey.Builder((RSAPublicKey) googleKey.getPublic()).keyID(GOOGLE_KEY_ID)
                    .algorithm(JWSAlgorithm.RS256).build();
            respond(exchange, 200, new JWKSet(jwk).toString());
        }

        private void steamVerify(HttpExchange exchange) throws IOException {
            steamVerifications.add(form(exchange));
            respond(exchange, 200, "ns:" + "http://specs.openid.net/auth/2.0\nis_valid:" + steamValid + "\n");
        }

        private static Map<String, String> form(HttpExchange exchange) throws IOException {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            return Stream.of(body.split("&")).filter(pair -> pair.contains("=")).collect(Collectors.toMap(
                    pair -> URLDecoder.decode(pair.substring(0, pair.indexOf('=')), StandardCharsets.UTF_8),
                    pair -> URLDecoder.decode(pair.substring(pair.indexOf('=') + 1), StandardCharsets.UTF_8),
                    (first, second) -> second));
        }

        private static void respond(HttpExchange exchange, int status, String body) throws IOException {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            try (var out = exchange.getResponseBody()) {
                out.write(bytes);
            } catch (IOException exception) {
                throw new UncheckedIOException(exception);
            }
        }
    }
}
