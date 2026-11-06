# AUTH REHBERİ — TEK SAYFA

**Sahip:** Kerem (auth-service) · **Güncelleme:** 2026-11-06 · **Durum:** güncel; çelişen eski belgede bu sayfa kazanır

Bu sayfa, Auth'a bağlanacak her servis sahibi için **tek giriş noktasıdır**. Önce burayı oku. Ayrıntı gereken yerde ilgili belgeye bağlantı var. Kod örnekleri gerçek servislerden (Game, Review, Library, User) sadeleştirilerek alındı.

---

## 1. Üç cümlede Auth

1. Auth kimliği doğrular ve **imzalı JWT** üretir. Parola, oturum, rol, hesap durumu, 2FA ve Steam/Google kimlikleri yalnız Auth'tadır.
2. Her servis token'ı **kendisi doğrular** (Auth'a her istekte sormaz, Gateway'e güvenmez). Hangi kaynağın kime ait olduğu **servisin kendi işidir**.
3. Servisler birbirine ve Auth'a **kısa ömürlü servis token'ıyla** konuşur. Kimseye varsayılan yetki yoktur; her (çağıran, hedef, kapsam) çifti Auth'ta tek tek tanımlıdır.

```text
 Tarayıcı ──cookie──▶ API Gateway ──Authorization: Bearer <at+jwt>──▶ Servis  (JWKS ile kendi doğrular)
                       (çerez→Bearer, CSRF, CORS)

 Servis A ──POST /internal/v1/service-tokens──▶ Auth        (clientId + secret + audience)
 Servis A ◀── svc+jwt (60 sn, aud=B, scope=…) ──┘
 Servis A ──Bearer svc+jwt──▶ Servis B                       (B: typ, aud, scope, sub doğrular)

 Auth ──DELETE /internal/v1/accounts/{id}──▶ her servis      (hesap silme, svc+jwt, 30 sn)
 Auth ──POST  olay zarfı (outbox)────────▶ AUTH_EVENT_TARGETS (svc+jwt, scope=events:ingest)
 Auth ──POST  /internal/v1/mail/…────────▶ Mail              (kod ve güvenlik bildirimi e-postaları)
```

---

## 2. Üç token türü

| | Kullanıcı token'ı | Servis token'ı | Cihaz token'ı |
|---|---|---|---|
| Başlıkta `typ` | `at+jwt` | `svc+jwt` | `dev+jwt` |
| Kim alır | Giriş yapan kullanıcı | Servis (client credentials) veya Auth (silme, olay) | Masaüstü cihaz |
| `sub` | hesap UUID'si | `svc:<servis>` (örn. `svc:game-service`) | cihaz UUID'si |
| `aud` | `ltz-api` | **tek değer**: hedef `<servis>-service` | tek hedef (`hardware-service` / `performance-service`) |
| Yetki | `roles` claim'i | `scope` claim'i (boşlukla ayrık) | tek kapsam (`hardware:sync:write` / `performance:sync:write`) |
| Ömür | **60 sn** | **≤ 60 sn** (silme çağrısında 30 sn) | kısa, uç sözleşmesi `docs/api.md` |
| Kullanıcı claim'leri (`roles`, `sid`, `ver`, `amr`) | var | **yok** | yok |

**Altın kural:** Her uç yalnız **kendi** token türünü kabul eder. Kullanıcı uçlarında `svc+jwt`, `/internal/**` uçlarında `at+jwt` **reddedilir**.

Ortak: RS256, imza Auth'un JWKS'inde (`${AUTH_SERVICE_URL}/api/v1/auth/.well-known/jwks.json`), `iss=ltz-auth`, `kid` zorunlu, saat sapması en çok 30 sn.
JWKS'te **birden fazla anahtar** olabilir (rotasyon sırasında `AUTH_JWT_ADDITIONAL_PUBLIC_KEYS`): tek anahtar varsaymayın, `kid` ile seçin, bilinmeyen `kid`'de JWKS'i bir kez yenileyin.

Kullanıcı token'ında **olmayanlar:** e-posta, kullanıcı adı, hesap durumu, `scope`. Profil User Service'tedir.
Claim'ler: `sub`, `roles` (öneksiz: `PLAYER`, `CONTENT_CREATOR`, `GAME_DEVELOPER`, `MODERATOR`, `ADMIN`), `sid`, `ver`, `amr` (`pwd`, `fed`, `otp`, `rc`), `jti`.
`sid` ve `ver`'i yalnız Auth anlamlandırabilir; sizin yapacağınız bir şey yok.

---

## 3. Kullanıcı token'ını doğrulamak (her servis)

```java
@Bean
JwtDecoder userJwtDecoder(@Value("${ltz.auth.jwks-uri}") String jwksUri,
                          @Value("${ltz.auth.issuer}") String issuer,
                          @Value("${ltz.auth.audience}") String audience) {
    NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwksUri)
            .jwsAlgorithm(SignatureAlgorithm.RS256)      // alg=none ve HS256 reddedilir
            .validateType(false)                         // typ aşağıda açıkça denetlenir
            .build();
    decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
            new JwtTimestampValidator(Duration.ofSeconds(30)),
            new JwtIssuerValidator(issuer),                                   // ltz-auth
            new JwtTypeValidator("at+jwt"),                                   // svc+jwt kullanıcı ucunda geçmez
            new JwtClaimValidator<List<String>>("aud", a -> a != null && a.contains(audience)),  // ltz-api
            new JwtClaimValidator<Object>("sub", Objects::nonNull)));
    return decoder;
}

@Bean
JwtAuthenticationConverter userAuthenticationConverter() {
    var authorities = new JwtGrantedAuthoritiesConverter();
    authorities.setAuthoritiesClaimName("roles");
    authorities.setAuthorityPrefix("ROLE_");
    var converter = new JwtAuthenticationConverter();
    converter.setJwtGrantedAuthoritiesConverter(authorities);
    return converter;
}
```

Güvenlik zinciri **varsayılan reddet** olmalı (`anyRequest().denyAll()`); her uç için rol açıkça yazılır.

### Kimlik `sub`'dan, sahiplik sizde

```java
@PutMapping("/reviews/{reviewId}")
@PreAuthorize("hasAnyRole('PLAYER','MODERATOR','ADMIN')")
ReviewResponse update(@PathVariable UUID reviewId, @AuthenticationPrincipal Jwt jwt, @RequestBody UpdateReview body) {
    UUID actor = UUID.fromString(jwt.getSubject());               // kimlik YALNIZ token'dan
    boolean moderator = jwt.getClaimAsStringList("roles").contains("MODERATOR");
    // Servis kendi DB'sinde denetler: review.userId == actor (ya da moderatör + denetim kaydı).
    // Sahibi değilse 404 (güvenli gizleme) veya 403; seçimi tutarlı yapın.
    return reviews.update(reviewId, actor, moderator, body);
}
```

- İstek gövdesine/başlığına `userId` **koymayın**; geldiyse yok sayın. `X-User-Id` gibi başlıklara güvenmeyin.
- Rol "ne yapabilir"i söyler, "hangi kaynağa dokunabilir"i söylemez. Oyun bazlı geliştirici yetkisi gibi sahiplik Auth'ta **yoktur**, Game'in kendi tablosundadır.
- Banlı/askıdaki ya da çıkış yapan hesap token'ı **en fazla 90 sn** (60 + 30 sn sapma) sizde geçerli görünür. Bu bilinen ve kabul edilmiş sınırdır. Para gibi geri alınamaz işlemlerde ek önlemi servis kendi tasarlar.
- Hata: token yok/bozuk → `401`; geçerli ama yetersiz → `403`. Gövde `ProblemDetail`, içinde token, claim, SQL, stack trace olmaz.

---

## 4. Servis → servis çağrısı

### 4.1 Çağıran taraf: token al

```java
// POST {AUTH_SERVICE_URL}/internal/v1/service-tokens
record TokenRequest(String clientId, String clientSecret, String audience) {}
record TokenResponse(String accessToken, String tokenType, long expiresIn, String scope) {}

Optional<String> tokenFor(String audience, String requiredScope) {
    if (secret.isBlank()) return Optional.empty();                         // yapılandırılmamış = çağrı yok
    try {
        TokenResponse r = client.post().uri("/internal/v1/service-tokens")
                .body(new TokenRequest("game-service", secret, audience))
                .retrieve().body(TokenResponse.class);
        boolean ok = r != null && r.expiresIn() <= 60
                && Set.of(r.scope().split(" ")).contains(requiredScope);   // beklenen kapsam yoksa token'ı at
        return ok ? Optional.of(r.accessToken()) : Optional.empty();
    } catch (RestClientException e) {
        return Optional.empty();                                           // log'a secret/token yazma; 401/403'te yeniden deneme yok
    }
}
```

- Gizli anahtar **yalnız ortam değişkeninden** gelir: `AUTH_<SERVİS>_SERVICE_CLIENT_SECRET` (en az 32 karakter; **boşsa o istemci kapalıdır**). Üretici: `node ops/generate-auth-dev-keys.mjs`.
- Token hedef (`audience`) başına ayrı alınır; süresi dolmadan ~10 sn önce yenilenir. Kullanıcı token'ına **geri düşülmez**: token alınamazsa çağrı yapılmaz (**fail-closed**).
- Hata kodları: yanlış sır `401 INVALID_CLIENT`; çift tanımlı değil `403 SERVICE_AUDIENCE_FORBIDDEN`.
- **Hesaba bağlı kapsam** (`signals:read`, `devices:verify`, `summary:read`, `reference:read`): istekte `accountId` zorunlu, token `account_id` taşır; hesap `ACTIVE` değilse `403 SERVICE_TARGET_ACCOUNT_UNAVAILABLE`. Bağsız kapsamda `accountId` verilmez (`400 SERVICE_ACCOUNT_BINDING_INVALID`). Aynı hedefte kapsamlar ya hep bağlı ya hiç.
- Kullanıcı adına çağrıda Auth "delegasyon token'ı" üretmez; servis, kullanıcının kendi `at+jwt`'sini iletir, hedef `viewer`'ı `sub`'dan okur (saklanmaz, loglanmaz, arka planda kullanılmaz).

### 4.2 Alıcı taraf: servis token'ı kabul et

```java
static OAuth2TokenValidator<Jwt> serviceTokenValidator(String issuer, String myAudience, Clock clock) {
    var skew = Duration.ofSeconds(30);
    var timestamps = new JwtTimestampValidator(skew);
    OAuth2TokenValidator<Jwt> contract = jwt -> {
        var now = clock.instant();
        boolean valid = "svc+jwt".equals(jwt.getHeaders().get("typ"))
                && List.of(myAudience).equals(jwt.getAudience())            // TEK değer, kendi adın
                && jwt.getSubject() != null && jwt.getSubject().startsWith("svc:")
                && jwt.getId() != null && jwt.getIssuedAt() != null && jwt.getExpiresAt() != null
                && Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt()).compareTo(Duration.ofSeconds(60)) <= 0
                && !jwt.getExpiresAt().isAfter(now.plusSeconds(60).plus(skew));
        return valid ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Geçersiz servis token'ı.", null));
    };
    return new DelegatingOAuth2TokenValidator<>(new JwtIssuerValidator(issuer), timestamps, contract);
}

// Yetki: yalnız "scope" claim'i, roles okunmaz. scp yedeği yetki vermesin diye claim adı açıkça verilir.
var scopes = new JwtGrantedAuthoritiesConverter();
scopes.setAuthoritiesClaimName("scope");                                   // "a:read b:read" → SCOPE_a:read, SCOPE_b:read

// Güvenlik zincirinde: /internal/** için servis decoder'ı, /api/** için kullanıcı decoder'ı kullanılır.
.requestMatchers(HttpMethod.GET, "/internal/v1/games/{id}/references").hasAuthority("SCOPE_game-references:read")
```

Ek denetimler uca göre: `sub` **tam** eşleşme (`svc:game-service`; yalnız `svc:` öneki yetmez), hesaba bağlı kapsamda `account_id` yoldaki hesapla eşit.

### 4.3 Tanımlı (çağıran → hedef) çiftleri

Listede olmayan çift `403`. Yeni çift = Auth'ta kod + test + bu tablo; istek `docs-auth/verilen-cevaplar/<servis>/talep-NN-*.md` olarak yazılır.

| Çağıran | Hedef | Kapsam | Ne için |
|---|---|---|---|
| admin | auth (`aud=auth-service`) | `account:read`, `account:status:write`, `account:role:write` | Hesap/rol/durum yönetimi (`actorId` Auth'ta yeniden denetlenir) |
| admin | community | `events:ingest` | `ModerationActionTaken` |
| social | community | `events:ingest` | `UserBlocked`, `UserUnblocked` |
| social | user | `privacy:read`, `events:ingest` | Arkadaş listesi görünürlüğü; arkadaşlık/engel olayları |
| community | admin, notification | `events:ingest` | `ContentReported`; bildirimler |
| messaging, lfp | social | `blocks:check` | Mesaj/eşleşme öncesi engel sorgusu |
| library | user | `privacy:read`, `events:ingest` | Koleksiyon gizliliği; `LibrarySummaryChanged` |
| hardware | user | `privacy:read` | Donanım görünürlük politikası |
| hardware | performance | `devices:verify summary:read` (hesaba bağlı) | Cihaz hesaba ait/aktif mi; donanım profili performans özeti |
| recommendation | performance | `reference:read` (hesaba bağlı) | Hesabın oyun performans referansı |
| review | user | `events:ingest` | `ReviewSummaryChanged` |
| recommendation | library | `signals:read` (hesaba bağlı) | Tek hesabın öneri sinyali |
| game | review | `rating-summaries:read`, `game-references:read` | Puan özeti; referans sayısı |
| game | library | `game-references:read` | Dış kimlik taşıma öncesi referans sayısı |

`ltz.auth.service-clients.clients.*` ayarları (`application.properties`) ve `ServiceClientConfig` tek doğruluk kaynağıdır; ayrıntı ve gerekçeler `servis-sozlesmeleri.md` §3.4–3.5.
**User, Mail ve Notification istemci değildir:** kimseyi senkron çağırmazlar, veri onlara olayla gelir.

---

## 5. Hesap silme ucu (verisi olan her servis yazar)

Auth, silme isteği 14 gün geri alınmadıysa 16 hedef servise `DELETE /internal/v1/accounts/{accountId}` çağırır. Hepsi `2xx` verince Auth kaydı **tamamen** silinir (silinen kullanıcının izi tutulmaz).

```java
@RestController
public class AccountErasureController {
    @DeleteMapping("/internal/v1/accounts/{accountId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void erase(@PathVariable UUID accountId, @AuthenticationPrincipal Jwt token) {
        // Kapsam (SCOPE_account:erase) güvenlik zincirinde zorunlu; burada kimlik ve hesap bağı:
        if (!"svc:auth-service".equals(token.getSubject())
                || !accountId.toString().equals(token.getClaimAsString("account_id")))
            throw new AccessDeniedException("Servis token'ı bu silme için geçerli değil.");
        erasure.erase(accountId);       // kişisel veriyi silen/anonimleştiren, idempotent iş
    }
}
```

| Kural | Değer |
|---|---|
| Token | `svc+jwt`, `aud=<kendi adın>`, `sub=svc:auth-service` (tam), `scope=account:erase`, `account_id`=yoldaki hesap, ömür 30 sn |
| Başarı | Silme **bittiğinde** `204` (veya `200`). Veri yoksa/zaten silinmişse de `204` |
| Onay sayılmaz | `202`, `3xx`, `404`, `401/403`, `5xx`, 5 sn zaman aşımı → Auth üstel geri çekilmeyle (1 dk → 6 sa) yeniden dener |
| Gateway | `/internal/**` Gateway'e açılmaz |
| Verisi olmayan servis | Yine ucu açıp `204` döner; "uygulanamaz" Auth'ta kabul edilmez |
| Log | Token ve hesap verisi loglanmaz |

**Durum (2026-11-06, kodda doğrulandı):** ucu yazılmış 9 servis: Game, Hardware, Library, Performance, Review, Social, User, Community, Mail.
**Eksik 7:** Messaging, LFP, Content, Event, Recommendation, Admin, Notification (hesap bunlar yazılana kadar tam silinmez).
Adım adım: `d4-ekip-notlari/01_ORTAK_SILME_UCU_UYGULAMA_REHBERI.md`.

---

## 6. Auth olaylarını almak (`events:ingest`)

Auth, olayları aynı işlemde `auth_events` tablosuna (outbox) yazar; işçi hedeflere HTTP ile iletir. **En az bir kez** teslim: alıcı `eventId` ile tekilleştirir. Hedef listesi `AUTH_EVENT_TARGETS` (varsayılan boş; dev şablonunda `user`). Hedef henüz yayında değilse teslim yeniden denenir, olay kaybolmaz.

| Olay | Ne zaman | Yük |
|---|---|---|
| `UserRegistered` | Hesap aktif olunca | hesap kimliği |
| `AccountStatusChanged` | **Yalnız yönetici** durum değiştirince | yeni durum |
| `AccountEmailChanged` | E-posta değişince | hesap kimliği |
| `ExternalIdentityLinked` / `Unlinked` | Steam/Google bağlanınca/kaldırılınca | `provider` (+ SteamID64 herkese açık kimliktir) |

Zarf: `eventId, eventType, eventVersion, occurredAt, producer, aggregateId, payload`. E-posta ve parola olaylara **konmaz**. Silme isteği/iptal/tamamlanma olay üretmez; onlar silme koordinasyonuyla yürür.

```java
@PostMapping("/internal/events")                        // yalnız scope=events:ingest (güvenlik zincirinde)
EventAck ingest(@Valid @RequestBody EventEnvelope e, @AuthenticationPrincipal Jwt token) {
    boolean callerIsProducer = ("svc:" + e.producer()).equals(token.getSubject());       // başkası adına olay yok
    boolean accountBound = !"auth-service".equals(e.producer())
            || e.aggregateId().toString().equals(token.getClaimAsString("account_id"));  // Auth olayı o hesaba bağlı
    if (!callerIsProducer || !accountBound) throw new AccessDeniedException("Bu olay için geçersiz token");
    return new EventAck(e.eventId(), events.ingestOnce(e));   // eventId ile tekilleştir; bilinmeyen tür için kendi kuralın
}
```

Aynı kalıp servisler arası olaylarda da geçerli (`ModerationActionTaken` yalnız `svc:admin-service` ile gelir).

---

## 7. Mail (Auth → Mail)

Auth e-postayı kendisi göndermez. **Prod'da** `AUTH_MAIL_ADAPTER=mail-service` zorunludur; yoksa Auth açılmaz. Auth, Mail'e kısa ömürlü servis token'ıyla (`aud=mail-service`, `scope=mail:send`, `account_id`) yazar: doğrulama/sıfırlama/silme kodları ve **güvenlik bildirimleri** (`POST /internal/v1/mail/security-notifications`, 11 tür: parola değişti, e-posta değişti, hesap askıya alındı/yasaklandı/kapatıldı/yeniden açıldı, 2FA açıldı/kapandı, kurtarma kodu kullanıldı, harici hesap bağlandı, ...). Bildirim **en iyi çaba**: başarısızlık işlemi geri almaz. Gövdede parola, kod, IP bulunmaz. Mail'in sözleşmesi: `verilen-cevaplar/mail-service/`.
Dev'de Mailpit kullanılır (`http://127.0.0.1:8025`, gerçek e-posta gitmez).

---

## 8. Tarayıcı, Gateway ve frontend (kısa)

- Giriş çerezle yürür. Gateway, `/api/v1/**` isteğinde `Authorization` yoksa erişim çerezini (`__Host-ltz_access` / `ltz_access`) Bearer'a çevirir; servisler **yalnız Bearer görür**.
- Çerezle gelen durum değiştiren isteklerde `X-XSRF-TOKEN` = `XSRF-TOKEN` çerezi olmalı, aksi halde `403 CSRF_TOKEN_INVALID`. `Origin` izinli listede olmalı. CORS tam eşleşme (`*` yasak).
- Gateway `X-User-*`, `X-Account-*`, `X-Auth-*`, `X-Internal-*`, `X-Role(s)` başlıklarını **her zaman siler**. Yine de kimliği kendin doğrula.
- `/api/v1/auth/**` IP başına dakikada 120 istekle sınırlıdır (`429` + `Retry-After`; sayaç örnek belleğindedir).
- **"Ben kimim":** `GET /api/v1/auth/me` → `{accountId, roles, authMethods, accessTokenExpiresAt}` (`no-store`; e-posta ve profil yok, o User'da). `401` = misafir ya da oturum geçersiz: önce `POST /session/refresh` dene, o da `401` verirse misafirdir. Sayfa yenilenince arayüz kimlik ve rolü buradan öğrenir (HttpOnly çerez JS'ten okunamaz). `roles` yalnız menü gibi arayüz içindir; yetki kararı servislerde token'dan verilir.
- Uçların tamamı: `docs/api.md` → `## AUTH`. Frontend akışları (cookie, CSRF, giriş): `docs/EKIP_AUTH_GATEWAY_ENTEGRASYON_NOTLARI.md`, `entegrasyon/gateway-ve-frontend.md`.

Auth uç aileleri (`/api/v1/auth`): `register`, `email-verification`, `login` (+ `login/2fa`, `enrollment`), `me`, `csrf`, `session` (`refresh`, `logout`, `logout-all`), `2fa/totp` (`setup`, `activate`, `disable`), `oauth` (Google/Steam giriş ve bağlama), `password` (`change`, `set`, `forgot`, `reset`), `email/change`, `account/deletion` (`request`, `cancel`), `devices`, `admin/**` (yalnız ADMIN). İç uçlar `/internal/v1`: `service-tokens`, `accounts/{id}` (+ `status`, `roles/grant`, `roles/revoke`).

---

## 9. Yerelde çalıştırma

```text
node ops/generate-auth-dev-keys.mjs      # tek komut: .env.dev'i tamamlar, anahtarları üretir, kuralları denetler (var olana dokunmaz)
.\ops\env.cmd use dev                    # (Linux/macOS: sh ./ops/env.sh use dev)
docker compose -p ltz-dev --env-file .env.dev --profile dev-mail up -d --build auth-service
node ops/auth-smoke.mjs                  # Auth + Gateway uçtan uca duman testi
```

| Konu | Dev | Prod |
|---|---|---|
| E-posta | Mailpit | Yalnız `mail-service`; yoksa Auth açılmaz |
| Steam hesabında 2FA | Kapalı (hızlı deneme) | Açık; kapatılırsa Auth açılmaz |
| ADMIN için 2FA | Gevşetilebilir (yalnız dev/test) | Zorunlu |
| Anahtarlar | Betikle yerel | Ayrı üretilir, sır yöneticisinde; dev anahtarı kullanılmaz |
| İç adresler | `http` olabilir | `https` zorunlu |
| Parola (≥15, kara liste), token ömrü 60 sn, CSRF, çerez öznitelikleri | Aynı | Aynı |

Sorun giderme:

| Belirti | Neden → çözüm |
|---|---|
| Açılışta anahtar hatası (`AUTH_OTP_HMAC_KEY`, `AUTH_TOTP_ENCRYPTION_KEY`) | Boş/kısa/Base64 değil → betiği çalıştır; `node ops/auth-env-check.mjs` hangisini söyler |
| `prod`'da açılmıyor | Mail adaptörü `mail-service` değil ya da güvenlik ayarı gevşetilmiş → hata mesajı ayarı söyler |
| Kod e-postası gelmiyor | `COMPOSE_PROFILES=dev-mail` yok |
| Yeni uç `403` veriyor | Auth imajı eski: `--build` ile yeniden derle (bilinmeyen yol varsayılan reddedilir) |
| `409 PASSWORD_REQUIRED` | Hesap Google/Steam ile açılmış, parolası yok → `password/set/request` sonra `password/set` |
| Girişte `MFA_ENROLLMENT_REQUIRED` | Steam hesabında 2FA zorunlu, kurulu değil → `login/2fa/enrollment/start` sonra `complete` |
| DB "checksum mismatch" | Uygulanmış migration dosyası değişmiş → yerel DB'yi sıfırla; uygulanmış migration'a asla dokunma |
| İlk ADMIN nasıl olur? | Prod yolu **bilinçli olarak bekliyor** (B7). Dev sırası: kayıt+doğrulama, giriş, TOTP kur+aktifleştir, SQL ile ADMIN rolü ekle, yeniden giriş |

---

## 10. Yapma listesi

- Anahtar, parola, token'ı koda, commit'e, loga, sohbete yazma. `.env*` Git'te değildir.
- Dev anahtarını prod'a, prod anahtarını yerele kopyalama.
- Test geçsin diye CSRF, token ömrü, parola kuralı, yetki kontrolü kapatma. İzinli dev gevşetmeleri yeter.
- Gateway'e ya da `X-User-Id` gibi başlığa güvenme; JWKS ile kendin doğrula.
- Auth DB'sine bağlanma; Auth'a yalnız bu sayfadaki sözleşmelerle konuş.
- Uygulanmış migration'ı değiştirme. Auth'un son boş migration numarası **V70** (V61–V69 kullanımda); aralık için Kerem'e sor.
- Auth'tan yeni uç/durum/hata kodu isteme, `verilen-cevaplar/<servis>/` altına talep yaz.
- **Auth'un 15 dosyasının imzasına dokunma** (aşağıda §12).

---

## 11. Servisin Auth entegrasyon testleri (kontrol listesi)

- [ ] Token yok → 401; bozuk imza → 401; süresi dolmuş → 401
- [ ] `alg=none` ve HS256 → 401
- [ ] Yanlış `iss`, yanlış `aud`, yanlış `typ` → 401 (kullanıcı ucuna `svc+jwt`, iç uca `at+jwt` dahil)
- [ ] Geçerli token, yetersiz rol → 403; `PLAYER` ile yönetici ucu → 403
- [ ] Başkasının kaynağı → 404/403, veri sızmaz; gövdedeki sahte `userId` yok sayılır
- [ ] Bilinmeyen `kid` → JWKS bir kez yenilenir, sonra 401
- [ ] İç uç: yanlış `sub`, yanlış `scope`, başka hesabın `account_id`'si → 403; ömür > 60 sn → 401
- [ ] Silme ucu: aynı çağrı iki kez → ikisi de 2xx; kimliksiz → 401; veri gerçekten silindi
- [ ] Olay alıcısı: aynı `eventId` iki kez → ikinci kez işlenmez; `producer` ≠ `sub` → 403
- [ ] Hata gövdesi `ProblemDetail`; loglarda token/kişisel veri yok
- [ ] `iss`/`aud` değerleri ortak yapılandırmadan gelir (koda gömülü değil)
- [ ] Güçlü doğrulama için Auth'un gerçek `ServiceTokenIssuer`'ıyla token üreten test (Review'ın `InternalRatingSummaryTest` kalıbı)

---

## 12. Auth geliştiricisi için: dokunulmaz imzalar

Game, Hardware, Library ve Review testleri Auth'un 15 kaynak dosyasını **doğrudan derleyip** yansımayla çağırır: `Account`, `AuthSession`, `Role`, `AccountStatus`, `RoleCode`, `AuthMethod`, `SessionRevokeReason`, `JwtProperties`, `JwtSigningKey`, `TokenClaims`, `AccessTokenIssuer`, `ServiceTokenIssuer`, `DeviceTokenIssuer`, `JwksController`, `AuthApiPaths`.

- Bu dosyaların kamu imzaları değişmez; yeni davranış **düz tiplerle** (`String`, `UUID`, `Duration`) taşınır. Küme dışı bir Auth tipine (yeni enum, DTO, servis) bağımlılık eklenirse tüketici testi `cannot find symbol` ile kırılır, Auth testleri geçer.
- `ConsumerContractTest` imzaları, `ConsumerSourceCompilationTest` derlemeyi kilitler. Bilerek değiştirilecekse önce servis sahiplerine haber, sonra testle birlikte güncelleme.

---

## 13. Bu rehber nasıl güncel kalır

- Sözleşme değişikliği **aynı PR'da** bu sayfayı günceller (yeni (çağıran, hedef) çifti, yeni olay, yeni token alanı, yeni zorunlu ayar).
- Çelişki sırası: **Auth kodu > bu rehber > `servis-sozlesmeleri.md` > diğer belgeler.**
- Bu sayfa kısa kalır: ayrıntı ilgili belgede, burada yalnız kural, kod örneği ve bağlantı.

## 14. Diğer belgeler: hangisi ne işe yarar

Klasör sade tutuldu (104 → 75 dosya). Faz kapanış raporları, süreç/commit planları, ekip uyum denetimi, boşluk raporu ve kapanmış talepler silindi; hepsi git geçmişindedir (`git log --diff-filter=D --name-only -- docs-auth`).

| Belge | Ne |
|---|---|
| [`servis-sozlesmeleri.md`](servis-sozlesmeleri.md) | **Geçerli referans.** Sözleşmelerin gerekçeli tam hali (§3.4–3.5 servis kimliği, olaylar) |
| [`entegrasyon/`](entegrasyon/README.md) | Servis servis "ne hazır, ne yapacaksın" dosyaları |
| [`verilen-cevaplar/`](verilen-cevaplar/README.md) | Servislerin Auth'a sorduklarına açık ve kalıcı cevaplar |
| [`d4-ekip-notlari/`](d4-ekip-notlari/01_ORTAK_SILME_UCU_UYGULAMA_REHBERI.md) | Silme ucu: ortak rehber ve kişiye özel notlar |
| [`ekip-hizli-baslangic.md`](ekip-hizli-baslangic.md) | Yerel çalıştırma ayrıntısı (bu rehberin §9'u özetidir) |
| [`anahtar-rotasyonu.md`](anahtar-rotasyonu.md), [`migration-yazim-kurallari.md`](migration-yazim-kurallari.md) | Konuya özel, geçerli |
| [`acik-guvenlik-borclari.md`](acik-guvenlik-borclari.md) | Açık güvenlik işlerinin kaydı |
| [`kerem-dis-adimlar-ve-canli-test.md`](kerem-dis-adimlar-ve-canli-test.md) | Auth sahibinin elle yapacakları ve canlı test planı |
| [`AUTH_SERVICE_KAPSAMLI_TEKNIK_REHBER.md`](AUTH_SERVICE_KAPSAMLI_TEKNIK_REHBER.md) | **Tarihî** (21 Ekim); iç tasarımı öğrenmek için, değerler eskimiş olabilir |
