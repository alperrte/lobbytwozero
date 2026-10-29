package com.ltz.hardware.infrastructure.security;

import com.ltz.hardware.infrastructure.persistence.PostgresIntegrationSupport;
import com.sun.net.httpserver.HttpServer;
import jakarta.servlet.http.Cookie;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real Auth issuer/JWKS and Gateway filters, Hardware HTTP security/controller and disposable PostgreSQL.
 * Does not claim deployed Auth login, reverse proxy or sixteen-service deletion E2E verification. */
@SpringBootTest(properties={"logging.level.root=INFO", "debug=false", "hardware.auth.integration-test=true"})
@AutoConfigureMockMvc
class HardwareAuthIntegrationTest extends PostgresIntegrationSupport {
    @org.springframework.boot.test.context.TestConfiguration(proxyBeanMethods=false)
    static class AuthTestConfiguration {
        @org.springframework.context.annotation.Bean @org.springframework.context.annotation.Primary
        HardwareAuthProperties hardwareTestAuthProperties() {
            return new HardwareAuthProperties(java.net.URI.create("http://127.0.0.1:"+JWKS.getAddress().getPort()),
                    "ltz-auth","ltz-api",java.time.Duration.ofMillis(500),java.time.Duration.ofSeconds(1));
        }
    }
    static final Path COMPILED;
    static final ProductionAuthSupport AUTH;
    static final HttpServer JWKS;
    static {
        try {
            COMPILED = Files.createTempDirectory("hardware-auth-contract-");
            AUTH = new ProductionAuthSupport(COMPILED);
            JWKS = HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
            byte[] body = AUTH.jwks().getBytes(StandardCharsets.UTF_8);
            JWKS.createContext("/api/v1/auth/.well-known/jwks.json", exchange -> {
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200,body.length);
                try (var output = exchange.getResponseBody()) { output.write(body); }
                exchange.close();
            });
            JWKS.start();
        } catch (Exception exception) { throw new ExceptionInInitializerError(exception); }
    }
    @AfterAll static void release() throws Exception {
        JWKS.stop(0); AUTH.close();
        try (var files = Files.walk(COMPILED)) {
            for (Path file : files.sorted(Comparator.reverseOrder()).toList()) Files.delete(file);
        }
    }
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired WebApplicationContext context;
    @Autowired com.ltz.hardware.infrastructure.persistence.repository.HardwareAccountAccessGuard guard;
    @Autowired com.ltz.hardware.application.service.HardwareAccountErasureService erasure;
    @Autowired org.springframework.transaction.PlatformTransactionManager transactions;
    static final String PROFILES="/api/v1/hardware/profiles";
    static final String BRANDS="/api/v1/hardware/catalog/brands";

    ResultActions send(MockHttpServletRequestBuilder request, String token, String body) throws Exception {
        request.header("Authorization", "Bearer "+token);
        if (body!=null) request.contentType("application/json").content(body);
        return mvc.perform(request);
    }
    String createProfile(UUID account) throws Exception {
        return send(post(PROFILES), AUTH.token(account,"PLAYER"), "{\"name\":\"Isolated PC\",\"deviceType\":\"DESKTOP\"}")
                .andExpect(status().isCreated()).andReturn().getResponse().getHeader("Location");
    }

    @Test void realAuthDeviceIdentityReachesOnlySnapshotAndKeepsPerformancePolicyClosed() throws Exception {
        UUID owner=UUID.randomUUID(), device=UUID.randomUUID();
        String profile=createProfile(owner);
        String token=AUTH.deviceToken(device,owner);
        String snapshot="""
            {"profileVersion":0,"consentGeneration":"%s","sequence":1,
             "scan":{"schemaVersion":1,"capturedAt":"%s","components":[
               {"componentKey":"%s","category":"CPU","modelName":"Test CPU","quantity":1}]}}
            """.formatted(UUID.randomUUID(),java.time.Instant.now(),UUID.randomUUID());
        send(post(profile+"/sync/snapshots").header("Idempotency-Key",UUID.randomUUID()),token,snapshot)
                .andExpect(status().isNotFound());
        send(get(profile),token,null).andExpect(status().isUnauthorized());
        send(get(profile+"/sync-consent"),token,null).andExpect(status().isUnauthorized());
        send(delete("/internal/v1/accounts/"+owner),token,null).andExpect(status().isUnauthorized());
        send(post(profile+"/sync/snapshots"),AUTH.token(owner,"ADMIN"),snapshot)
                .andExpect(status().isForbidden());
        send(post(profile+"/sync/snapshots"),AUTH.serviceToken(owner),snapshot)
                .andExpect(status().isUnauthorized());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM hardware_sync_consents WHERE profile_id=?",Integer.class,
                profileId(profile))).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM hardware_sync_receipts WHERE profile_id=?",Integer.class,
                profileId(profile))).isZero();
        erasure.erase(owner);
        send(post(profile+"/sync/snapshots").header("Idempotency-Key",UUID.randomUUID()),token,snapshot)
                .andExpect(status().isUnauthorized());
    }

    @ParameterizedTest @ValueSource(strings={"account_id","scope","jti","exp","iat","nbf","sub","aud","iss",
            "longLifetime","badOwner","badSubject","wrongScope","badSignature","missingKid","wrongType"})
    void invalidDeviceCredentialsNeverReachSnapshotPolicy(String scenario) throws Exception {
        UUID owner=UUID.randomUUID();
        var produced=com.nimbusds.jwt.SignedJWT.parse(AUTH.deviceToken(UUID.randomUUID(),owner));
        var claims=new HashMap<String,Object>(produced.getJWTClaimsSet().getClaims());
        var header=new com.nimbusds.jose.JWSHeader.Builder(produced.getHeader());
        var key=AUTH.key();
        switch(scenario) {
            case "longLifetime" -> claims.put("exp",new Date(System.currentTimeMillis()+120000));
            case "badOwner" -> claims.put("account_id","1-1-1-1-1");
            case "badSubject" -> claims.put("sub","svc:auth-service");
            case "wrongScope" -> claims.put("scope","hardware:sync:write-extra");
            case "badSignature" -> key=new com.nimbusds.jose.jwk.gen.RSAKeyGenerator(3072).generate();
            case "missingKid" -> header.keyID(null);
            case "wrongType" -> header.type(new com.nimbusds.jose.JOSEObjectType("svc+jwt"));
            default -> claims.remove(scenario);
        }
        String token=HardwareJwtTestTokens.signed(key,header.build(),claims);
        send(post(PROFILES+"/"+UUID.randomUUID()+"/sync/snapshots"),token,"{}")
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"));
    }

    @Test void realPlayerOwnershipMassAssignmentAndForgedHeadersRemainBoundToVerifiedSubject() throws Exception {
        UUID owner=UUID.randomUUID(), other=UUID.randomUUID();
        String path=createProfile(owner);
        send(get(path), AUTH.token(owner,"PLAYER"),null).andExpect(status().isOk());
        send(get(path).header("X-User-Id",owner).header("X-Role","ADMIN"),AUTH.token(other,"PLAYER"),null)
                .andExpect(status().isNotFound());
        send(post(PROFILES),AUTH.token(owner,"PLAYER"),"{\"name\":\"PC\",\"deviceType\":\"DESKTOP\",\"userId\":\""+other+"\"}")
                .andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("SELECT user_id FROM hardware_profiles WHERE id=?",UUID.class,
                UUID.fromString(path.substring(path.lastIndexOf('/')+1)))) .isEqualTo(owner);
    }

    @ParameterizedTest @ValueSource(strings={"PLAYER","CONTENT_CREATOR","GAME_DEVELOPER","MODERATOR","ADMIN"})
    void realCanonicalRolesHaveOnlyApprovedLocalPermissions(String role) throws Exception {
        UUID account=UUID.randomUUID(); String token=AUTH.token(account,role);
        send(get(BRANDS),token,null).andExpect(status().isOk());
        send(post(BRANDS),token,"{\"name\":\"Brand "+UUID.randomUUID()+"\",\"reason\":\"Isolated contract test\"}")
                .andExpect(role.equals("ADMIN") ? status().isCreated() : status().isForbidden());
        send(get("/api/v1/hardware/moderation/reviews/"+UUID.randomUUID()),token,null)
                .andExpect(Set.of("ADMIN","MODERATOR").contains(role) ? status().isNotFound() : status().isForbidden());
        // No canonical user role can be promoted to a desktop device identity.
        send(post(PROFILES+"/"+UUID.randomUUID()+"/sync/snapshots"),token,"{}")
                .andExpect(status().isForbidden());
    }

    @ParameterizedTest @ValueSource(strings={"ADMIN","admin","hardware:catalog:write","SCOPE_hardware:catalog:write"})
    void claimedPermissionStringsAndWrongRoleShapesDoNotGrantWrite(String role) throws Exception {
        UUID account=UUID.randomUUID();
        String token=HardwareJwtTestTokens.signed(AUTH.key(),account,false, c -> {
            c.put("roles",role); c.put("scope","hardware:catalog:write"); c.put("permissions",List.of("hardware:catalog:write"));
        });
        send(post(BRANDS),token,"{\"name\":\"Blocked\",\"reason\":\"Isolated test\"}") .andExpect(status().isForbidden());
    }

    @Test void productionGatewayCookieConversionRequiresCsrfAndAllowedOrigin() throws Exception {
        var gateway=MockMvcBuilders.webAppContextSetup(context).addFilters(AUTH.gatewayFilters()).apply(springSecurity()).build();
        Cookie access=new Cookie("ltz_access",AUTH.token(UUID.randomUUID(),"PLAYER"));
        Cookie csrf=new Cookie("XSRF-TOKEN","isolated-csrf");
        gateway.perform(get(PROFILES).cookie(access)).andExpect(status().isOk());
        gateway.perform(post(PROFILES).cookie(access,csrf)).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF_TOKEN_INVALID"));
        gateway.perform(post(PROFILES).cookie(access,csrf).header("X-XSRF-TOKEN","wrong")).andExpect(status().isForbidden());
        gateway.perform(post(PROFILES).cookie(access,csrf).header("X-XSRF-TOKEN",csrf.getValue())
                .header("Origin","https://evil.example")).andExpect(status().isForbidden());
        gateway.perform(post(PROFILES).cookie(access,csrf).header("X-XSRF-TOKEN",csrf.getValue())
                .header("Origin","https://app.example.test").contentType("application/json")
                .content("{\"name\":\"Cookie PC\",\"deviceType\":\"DESKTOP\"}")).andExpect(status().isCreated());
        mvc.perform(get(PROFILES).cookie(access)).andExpect(status().isUnauthorized());
    }

    @Test void realServiceIssuerIsAcceptedOnlyForBoundInternalErasureAndRetryIsIdempotent() throws Exception {
        UUID owner=UUID.randomUUID(), other=UUID.randomUUID();
        createProfile(owner); String otherProfile=createProfile(other);
        String token=AUTH.serviceToken(owner);
        send(delete("/internal/v1/accounts/"+owner),token,null).andExpect(status().isNoContent());
        send(delete("/internal/v1/accounts/"+owner),token,null).andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM hardware_profiles WHERE user_id=?",Integer.class,owner)).isZero();
        send(get(otherProfile),AUTH.token(other,"PLAYER"),null).andExpect(status().isOk());
        send(post(PROFILES),AUTH.token(owner,"PLAYER"),"{\"name\":\"Resurrection\",\"deviceType\":\"DESKTOP\"}")
                .andExpect(status().isUnauthorized()).andExpect(header().string("WWW-Authenticate","Bearer"));
        send(get(PROFILES),token,null).andExpect(status().isUnauthorized());
        send(delete("/internal/v1/accounts/"+owner),AUTH.token(owner,"ADMIN"),null).andExpect(status().isUnauthorized());
        send(delete("/internal/v1/accounts/"+other),token,null).andExpect(status().isForbidden());
    }

    @ParameterizedTest @ValueSource(strings={"sub","account_id","scope"})
    void signedServiceTokensStillRequireExactCallerAccountAndScope(String claim) throws Exception {
        UUID account=UUID.randomUUID();
        String token=HardwareJwtTestTokens.signed(AUTH.key(),account,true,c -> c.put(claim,
                claim.equals("sub") ? "svc:admin-service" : "wrong"));
        send(delete("/internal/v1/accounts/"+account),token,null).andExpect(status().isForbidden());
    }

    @ParameterizedTest @ValueSource(strings={"account_id","scope"})
    void missingErasureBindingOrScopeIsForbidden(String claim) throws Exception {
        UUID account=UUID.randomUUID();
        send(delete("/internal/v1/accounts/"+account),HardwareJwtTestTokens.signed(AUTH.key(),account,true,
                c -> c.remove(claim)),null).andExpect(status().isForbidden());
    }

    @Test void internalRoutesAndInvalidUuidHaveExplicitResponses() throws Exception {
        UUID account=UUID.randomUUID(); String token=AUTH.serviceToken(account);
        mvc.perform(delete("/internal/v1/accounts/"+account)).andExpect(status().isUnauthorized());
        send(get("/internal/v1/accounts/"+account),token,null).andExpect(status().isForbidden());
        send(delete("/internal/other"),token,null).andExpect(status().isForbidden());
        send(delete("/internal/v1/accounts/not-a-uuid"),token,null).andExpect(status().isBadRequest());
        send(delete("/internal/v1/accounts/"+account),token,null).andExpect(status().isNoContent());
    }

    @ParameterizedTest @ValueSource(strings={"exp","nbf","iat","sub","aud","iss","expired","futureNbf",
            "futureIat","longLifetime","wrongIssuer","wrongAudience","multipleServiceAudience","jti"})
    void invalidRawClaimsAndTimeBoundsAreRejectedForBothTokenKinds(String scenario) throws Exception {
        UUID account=UUID.randomUUID();
        for (boolean service : List.of(false,true)) {
            if (!service && Set.of("multipleServiceAudience","jti").contains(scenario)) continue;
            String token=HardwareJwtTestTokens.signed(AUTH.key(),account,service,c -> {
                switch (scenario) {
                    case "expired" -> { c.put("iat",new Date(System.currentTimeMillis()-180000));
                        c.put("nbf",new Date(System.currentTimeMillis()-180000)); c.put("exp",new Date(System.currentTimeMillis()-120000)); }
                    case "futureNbf" -> c.put("nbf",new Date(System.currentTimeMillis()+120000));
                    case "futureIat" -> c.put("iat",new Date(System.currentTimeMillis()+120000));
                    case "longLifetime" -> c.put("exp",new Date(System.currentTimeMillis()+(service?120000:1800000)));
                    case "wrongIssuer" -> c.put("iss","other-issuer");
                    case "wrongAudience" -> c.put("aud",List.of("social-service"));
                    case "multipleServiceAudience" -> c.put("aud",List.of("hardware-service","social-service"));
                    default -> c.remove(scenario);
                }
            });
            send(service ? delete("/internal/v1/accounts/"+account) : get(PROFILES),token,null)
                    .andExpect(status().isUnauthorized()).andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                    .andExpect(header().string("Cache-Control","private, no-store"));
        }
    }

    @ParameterizedTest @ValueSource(strings={"missingKid","unknownKid","wrongType","badSubject","badSignature","none","hs256"})
    void invalidHeadersSignaturesAndSubjectNeverReachBusinessCode(String scenario) throws Exception {
        UUID account=UUID.randomUUID();
        for (boolean service : List.of(false,true)) {
            var claims=HardwareJwtTestTokens.claims(account,service);
            var header=new com.nimbusds.jose.JWSHeader.Builder(com.nimbusds.jose.JWSAlgorithm.RS256)
                    .keyID(AUTH.key().getKeyID()).type(new com.nimbusds.jose.JOSEObjectType(service?"svc+jwt":"at+jwt"));
            String token;
            switch (scenario) {
                case "missingKid" -> { header.keyID(null); token=HardwareJwtTestTokens.signed(AUTH.key(),header.build(),claims); }
                case "unknownKid" -> { header.keyID("unknown-test-key"); token=HardwareJwtTestTokens.signed(AUTH.key(),header.build(),claims); }
                case "wrongType" -> { header.type(new com.nimbusds.jose.JOSEObjectType(service?"at+jwt":"svc+jwt"));
                    token=HardwareJwtTestTokens.signed(AUTH.key(),header.build(),claims); }
                case "badSubject" -> { claims.put("sub","untrusted-subject"); token=HardwareJwtTestTokens.signed(AUTH.key(),header.build(),claims); }
                case "badSignature" -> { var other=new com.nimbusds.jose.jwk.gen.RSAKeyGenerator(3072).generate();
                    token=HardwareJwtTestTokens.signed(other,header.build(),claims); }
                case "none" -> token=new com.nimbusds.jwt.PlainJWT(HardwareJwtTestTokens.claimSet(claims)).serialize();
                default -> { var jwt=new com.nimbusds.jwt.SignedJWT(new com.nimbusds.jose.JWSHeader.Builder(com.nimbusds.jose.JWSAlgorithm.HS256)
                            .type(new com.nimbusds.jose.JOSEObjectType(service?"svc+jwt":"at+jwt")).keyID(AUTH.key().getKeyID()).build(),
                            HardwareJwtTestTokens.claimSet(claims));
                    jwt.sign(new com.nimbusds.jose.crypto.MACSigner(new byte[32])); token=jwt.serialize(); }
            }
            send(service ? delete("/internal/v1/accounts/"+account) : get(PROFILES),token,null).andExpect(status().isUnauthorized());
        }
    }

    UUID profileId(String path) { return UUID.fromString(path.substring(path.lastIndexOf('/')+1)); }
    UUID review(UUID owner, UUID product) {
        UUID id=UUID.randomUUID();
        jdbc.update("""
            INSERT INTO hardware_reviews(id,product_id,user_id,body,body_hash,created_at,updated_at)
            VALUES (?,?,?,'Isolated review',?,now(),now())
            """,id,product,owner,com.ltz.hardware.domain.entity.HardwareReviewText.hash("Isolated review"));
        return id;
    }
    UUID event(UUID review,UUID owner,String state) {
        UUID id=UUID.randomUUID();
        jdbc.update("""
            INSERT INTO outbox_events(id,aggregate_type,aggregate_id,event_type,payload,occurred_at,status)
            VALUES (?,'HARDWARE_REVIEW',?,'HardwareReviewModerated',CAST(? AS jsonb),now(),?)
            """,id,review,"{\"payload\":{\"ownerId\":\""+owner+"\"}}",state);
        return id;
    }
    void audit(UUID actor,UUID review) {
        jdbc.update("INSERT INTO hardware_review_audit(id,actor_id,action,target_id,reason) VALUES (?,?,'CREATE_REVIEW',?,'Isolated audit')",
                UUID.randomUUID(),actor,review);
    }
    void report(UUID reporter,UUID review,UUID event) {
        jdbc.update("INSERT INTO hardware_review_reports(id,review_id,reporter_id,reason,outbox_event_id,created_at) VALUES (?,?,?,'SPAM',?,now())",
                UUID.randomUUID(),review,reporter,event);
    }

    @Test void erasesPersonalRowsAcrossAllTablesAndPreservesUnrelatedAccountCatalogAndPeerLimits() throws Exception {
        UUID a=UUID.randomUUID(),b=UUID.randomUUID(),c=UUID.randomUUID(),device=UUID.randomUUID();
        UUID profile=profileId(createProfile(a)), otherProfile=profileId(createProfile(b)), product=UUID.randomUUID();
        jdbc.update("INSERT INTO hardware_products(id,category,model,normalized_model) VALUES (?,'CPU',?,?)",product,product.toString(),product.toString());
        jdbc.update("INSERT INTO hardware_profile_components(id,profile_id,category,model_name,quantity,created_at,updated_at) VALUES (?,?,'CPU','Local',1,now(),now())",UUID.randomUUID(),profile);
        jdbc.update("INSERT INTO hardware_profile_field_visibility VALUES (?,'PROFILE_NAME','PRIVATE')",profile);
        jdbc.update("INSERT INTO hardware_profile_category_visibility VALUES (?,'CPU','PRIVATE')",profile);
        jdbc.update("""
            INSERT INTO hardware_sync_consents(id,profile_id,device_id,generation,status,granted_at,created_at,updated_at)
            VALUES (?,?,?,?,'GRANTED',now(),now(),now())
            """,UUID.randomUUID(),profile,device,UUID.randomUUID());
        jdbc.update("""
            INSERT INTO hardware_sync_receipts(id,profile_id,idempotency_key,generation,sequence,request_hash,response_body,created_at,expires_at)
            VALUES (?,?,?,?,1,?,'{}',now(),now()+interval '24 hours')
            """,UUID.randomUUID(),profile,UUID.randomUUID(),UUID.randomUUID(),"a".repeat(64));
        UUID reviewA=review(a,product),reviewB=review(b,product);
        audit(a,reviewA); audit(b,reviewA); audit(a,reviewB); audit(b,reviewB);
        for (UUID actor:List.of(a,b)) jdbc.update("INSERT INTO hardware_catalog_audit(id,actor_id,action,target_id,reason) VALUES (?,?,'CREATE_PRODUCT',?,'Local audit')",
                UUID.randomUUID(),actor,product);
        for(String state:List.of("PENDING","SENT","FAILED")) event(reviewA,a,state);
        UUID ownedReportEvent=event(reviewB,b,"PENDING"), foreignReportEvent=event(reviewA,a,"PENDING"), keptEvent=event(reviewB,b,"PENDING");
        report(a,reviewB,ownedReportEvent); report(b,reviewA,foreignReportEvent); report(c,reviewB,keptEvent);
        for(String key:List.of("actor:"+a,"moderator:"+a,"sync-user:"+a,"sync-apply-user:"+a,
                "sync-device:"+a+":"+device,"sync-apply-device:"+device,"actor:"+b,"peer:isolated-peer"))
            jdbc.update("INSERT INTO hardware_write_limits VALUES (?,date_trunc('minute',now()),1)",key);
        send(delete("/internal/v1/accounts/"+a),AUTH.serviceToken(a),null).andExpect(status().isNoContent());
        for(String table:List.of("hardware_profile_components","hardware_profile_field_visibility","hardware_profile_category_visibility",
                "hardware_sync_consents","hardware_sync_receipts"))
            assertThat(jdbc.queryForObject("SELECT count(*) FROM "+table+" WHERE profile_id=?",Integer.class,profile)).as(table).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM hardware_reviews WHERE user_id=?",Integer.class,a)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM hardware_review_reports WHERE reporter_id=? OR review_id=?",Integer.class,a,reviewA)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM hardware_review_audit WHERE actor_id=? OR target_id=?",Integer.class,a,reviewA)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM hardware_catalog_audit WHERE actor_id=?",Integer.class,a)).isZero();
        assertThat(jdbc.queryForList("SELECT id FROM outbox_events",UUID.class)).containsExactly(keptEvent);
        assertThat(jdbc.queryForList("SELECT bucket_key FROM hardware_write_limits",String.class)).containsExactlyInAnyOrder("actor:"+b,"peer:isolated-peer");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM hardware_profiles WHERE id=?",Integer.class,otherProfile)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM hardware_products WHERE id=?",Integer.class,product)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM hardware_review_audit WHERE actor_id=? AND target_id=?",Integer.class,b,reviewB)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM hardware_review_reports WHERE reporter_id=?",Integer.class,c)).isEqualTo(1);
        var fence=jdbc.queryForMap("SELECT * FROM hardware_account_erasure_fences WHERE account_hash=?",
                com.ltz.hardware.infrastructure.persistence.repository.HardwareAccountAccessGuard.accountHash(a));
        assertThat(fence.keySet()).containsExactlyInAnyOrder("account_hash","expires_at");
        assertThat(fence.toString()).doesNotContain(a.toString());
        Long seconds=jdbc.queryForObject("SELECT extract(epoch FROM expires_at-clock_timestamp())::bigint FROM hardware_account_erasure_fences WHERE account_hash=?",
                Long.class,com.ltz.hardware.infrastructure.persistence.repository.HardwareAccountAccessGuard.accountHash(a));
        assertThat(seconds).isBetween(1000L,1020L);
    }

    @Test void failingCleanupRollsBackFenceAndRowsAndNeverAcknowledgesDeletion() throws Exception {
        UUID account=UUID.randomUUID(); UUID profile=profileId(createProfile(account));
        jdbc.execute("CREATE FUNCTION hardware_test_erasure_failure() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'isolated failure'; END; $$");
        jdbc.execute("CREATE TRIGGER hardware_test_erasure_failure BEFORE DELETE ON hardware_profiles FOR EACH STATEMENT EXECUTE FUNCTION hardware_test_erasure_failure()");
        try {
            send(delete("/internal/v1/accounts/"+account),AUTH.serviceToken(account),null).andExpect(status().is5xxServerError());
            assertThat(jdbc.queryForObject("SELECT count(*) FROM hardware_profiles WHERE id=?",Integer.class,profile)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM hardware_account_erasure_fences WHERE account_hash=?",Integer.class,
                    com.ltz.hardware.infrastructure.persistence.repository.HardwareAccountAccessGuard.accountHash(account))).isZero();
        } finally {
            jdbc.execute("DROP TRIGGER hardware_test_erasure_failure ON hardware_profiles");
            jdbc.execute("DROP FUNCTION hardware_test_erasure_failure()");
        }
        send(delete("/internal/v1/accounts/"+account),AUTH.serviceToken(account),null).andExpect(status().isNoContent());
    }

    @Test void largeAccountCommitsBoundedBatchesAndOnlyAcknowledgesTheFinalOne() throws Exception {
        UUID account=UUID.randomUUID();
        jdbc.update("""
            INSERT INTO hardware_profiles(id,user_id,name,device_type,created_at,updated_at)
            SELECT gen_random_uuid(),?,'Batch PC '||n,'DESKTOP',now(),now() FROM generate_series(1,41) n
            """,account);
        String path="/internal/v1/accounts/"+account;
        String token=AUTH.serviceToken(account);
        for(int remaining:List.of(21,1)) {
            send(delete(path),token,null).andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.code").value("ACCOUNT_ERASURE_PENDING"))
                    .andExpect(header().string("Retry-After","60"));
            assertThat(jdbc.queryForObject("SELECT count(*) FROM hardware_profiles WHERE user_id=?",Integer.class,account)).isEqualTo(remaining);
            send(post(PROFILES),AUTH.token(account,"PLAYER"),"{\"name\":\"Blocked\",\"deviceType\":\"DESKTOP\"}")
                    .andExpect(status().isUnauthorized());
        }
        send(delete(path),token,null).andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM hardware_profiles WHERE user_id=?",Integer.class,account)).isZero();
    }

    @Test void boundedOutboxAndAuditCleanupNeverOrphansReferencesBetweenRetries() throws Exception {
        UUID account=UUID.randomUUID(),product=UUID.randomUUID();
        jdbc.update("INSERT INTO hardware_products(id,category,model,normalized_model) VALUES (?,'CPU',?,?)",product,product.toString(),product.toString());
        UUID review=review(account,product);
        jdbc.update("""
            INSERT INTO outbox_events(id,aggregate_type,aggregate_id,event_type,payload,occurred_at)
            SELECT gen_random_uuid(),'HARDWARE_REVIEW',?,'ContentReported','{}',now() FROM generate_series(1,2001)
            """,review);
        jdbc.update("""
            INSERT INTO hardware_review_audit(id,actor_id,action,target_id,reason)
            SELECT gen_random_uuid(),?,'CREATE_REVIEW',?,'Batch audit' FROM generate_series(1,1001)
            """,account,review);
        String path="/internal/v1/accounts/"+account; String token=AUTH.serviceToken(account);
        for(int remaining:List.of(1001,1)) {
            send(delete(path),token,null).andExpect(status().isServiceUnavailable());
            assertThat(jdbc.queryForObject("SELECT count(*) FROM outbox_events WHERE aggregate_id=?",Integer.class,review)).isEqualTo(remaining);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM hardware_reviews WHERE id=?",Integer.class,review)).isEqualTo(1);
        }
        send(delete(path),token,null).andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM outbox_events WHERE aggregate_id=?",Integer.class,review)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM hardware_review_audit WHERE actor_id=?",Integer.class,account)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM hardware_reviews WHERE id=?",Integer.class,review)).isZero();
    }

    @Test void auditImmutabilitySurvivesErasureIncludingUnrelatedRowsAndTruncate() {
        UUID account=UUID.randomUUID(),review=UUID.randomUUID(); audit(account,review);
        for(String sql:List.of("UPDATE hardware_review_audit SET reason='Changed'", "DELETE FROM hardware_review_audit", "TRUNCATE hardware_review_audit"))
            assertThatThrownBy(() -> jdbc.execute(sql)).isInstanceOf(org.springframework.dao.DataAccessException.class);
        var tx=new org.springframework.transaction.support.TransactionTemplate(transactions);
        assertThatThrownBy(() -> tx.executeWithoutResult(status -> {
            jdbc.queryForList("SELECT set_config('ltz.hardware.erasure_account',?,true)",UUID.randomUUID().toString());
            jdbc.update("DELETE FROM hardware_review_audit WHERE actor_id=?",account);
        })).isInstanceOf(org.springframework.dao.DataAccessException.class);
    }

    @Test void fenceExpiresAndPhysicalPurgeDoesNotDeleteActiveFences() throws Exception {
        UUID expired=UUID.randomUUID(),active=UUID.randomUUID();
        erasure.erase(expired); erasure.erase(active);
        jdbc.update("UPDATE hardware_account_erasure_fences SET expires_at=clock_timestamp()-interval '1 second' WHERE account_hash=?",
                com.ltz.hardware.infrastructure.persistence.repository.HardwareAccountAccessGuard.accountHash(expired));
        guard.clearExpiredFences();
        assertThatCode(() -> guard.requireActive(expired)).doesNotThrowAnyException();
        assertThatThrownBy(() -> guard.requireActive(active)).isInstanceOf(org.springframework.security.authentication.CredentialsExpiredException.class);
        assertThatThrownBy(() -> guard.lock(expired)).isInstanceOf(org.springframework.dao.InvalidDataAccessApiUsageException.class)
                .hasRootCauseInstanceOf(IllegalStateException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM hardware_account_erasure_fences WHERE account_hash=?",Integer.class,
                com.ltz.hardware.infrastructure.persistence.repository.HardwareAccountAccessGuard.accountHash(expired))).isZero();
    }

    @Test void concurrentWriterCommitsBeforeErasureOrIsRejectedAfterItNeverResurrectsData() throws Exception {
        UUID account=UUID.randomUUID(); String token=AUTH.token(account,"PLAYER");
        var tx=new org.springframework.transaction.support.TransactionTemplate(transactions);
        var locked=new java.util.concurrent.CountDownLatch(1);
        var release=new java.util.concurrent.CountDownLatch(1);
        try (var executor=java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
            var cleaning=executor.submit(() -> tx.executeWithoutResult(status -> {
                erasure.erase(account); locked.countDown();
                try { assertThat(release.await(2,java.util.concurrent.TimeUnit.SECONDS)).isTrue(); }
                catch (InterruptedException exception) { Thread.currentThread().interrupt(); throw new IllegalStateException(exception); }
            }));
            assertThat(locked.await(2,java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            var writing=executor.submit(() -> send(post(PROFILES),token,"{\"name\":\"Racing PC\",\"deviceType\":\"DESKTOP\"}"));
            release.countDown(); cleaning.get(3,java.util.concurrent.TimeUnit.SECONDS);
            writing.get(3,java.util.concurrent.TimeUnit.SECONDS).andExpect(status().isUnauthorized());
        } finally { release.countDown(); }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM hardware_profiles WHERE user_id=?",Integer.class,account)).isZero();
    }
}
