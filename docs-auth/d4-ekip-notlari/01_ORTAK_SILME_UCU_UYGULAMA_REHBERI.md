# Hesap Silme İç Ucu — Uygulama Rehberi (her silme hedefi servis için)

> Tarih: 2026-10-24 · Sözleşmenin sahibi: Auth (Kerem). Kaynak belge:
> [`docs-auth/servis-sozlesmeleri.md`](../servis-sozlesmeleri.md) §3–§3.3. **Auth bu kurallardan hiçbirini servise göre
> gevşetmez; servis Auth'a uyar.**

## 1. Ne bekleniyor

Hesap silme onaylandığında Auth, listedeki **her** servisi doğrudan (Gateway'den değil) çağırır:

```http
DELETE {SERVİS}_SERVICE_URL/internal/v1/accounts/{accountId}
Authorization: Bearer <servis token'ı>
```

Silme hedefleri (16): `user, mail, game, library, review, social, messaging, lfp, community, content, event, hardware,
recommendation, performance, admin, notification`. Adres: Compose'taki `<SERVİS>_SERVICE_URL` (varsayılan
`http://<servis>-service:8080`). **Yeni ENV gerekmez.**

**Hepsi `204` dönmeden hesap `DELETED` olmaz** ve kişisel veri Auth'ta kalır. Verisi olmayan servis de ucu açıp `204` döner;
"bizde veri yok, uç gerekmez" Auth tarafında kabul edilmez. Listeden çıkarma kararı yalnız Auth sahibinindir.

## 2. Token'da doğrulanacak her şey (hepsi ZORUNLU)

| Alan | Auth'un gönderdiği | Senin denetimin |
| --- | --- | --- |
| `typ` (başlık) | `svc+jwt` | Tam eşleşme. `at+jwt` (kullanıcı token'ı) iç uçta **reddedilir** |
| `alg`, `kid` | `RS256`, JWKS'teki anahtar | Yalnız RS256; imza Auth JWKS'i ile (`AUTH_SERVICE_URL/api/v1/auth/.well-known/jwks.json`; kullanıcı token'ıyla aynı) |
| `iss` | `ltz-auth` (`JWT_ISSUER`) | Eşit |
| `aud` | **tek değer** `<servis>-service` (ör. `game-service`) | Kendi adın. `ltz-api` iç uçta kabul edilmez |
| `sub` | `svc:auth-service` | **Tam eşleşme** (yalnız `svc:` öneki yetmez; silme yetkisi yalnız Auth'ta) |
| `scope` | `account:erase` | Uç `SCOPE_account:erase` yetkisi ister |
| `account_id` | silinecek hesap UUID'si | Yoldaki `{accountId}` ile **eşit**; değilse veya yoksa `403` |
| `iat`, `nbf`, `exp` | ömür 30 sn | `exp - iat ≤ 60 sn`, saat sapması ≤ 30 sn; `iat` yoksa ret |
| `jti` | her çağrıda yeni UUID | Var olmalı |
| `roles`, `sid`, `ver`, `amr` | **yok** | Servis token'ından kullanıcı yetkisi türetme |

## 3. Uç kuralları

- **Asla Gateway'e açılmaz.** Gateway yalnız `/api/v1/<servis>/**` yönlendirir; `/internal/**` için route ekleme.
- Kimliksiz açılmaz; yalnız §2'deki servis token'ıyla.
- Kişisel veriyi silmeyi/anonimleştirmeyi **bitirince** `204`. Zaten silinmişse veya veri yoksa da `204`. İkinci çağrı da `204`
  (idempotent).
- **Onay sayılmayanlar:** `202` ("sonra silerim" kabul edilmez), `3xx` (Auth yönlendirme izlemez), `404`, `401/403`, `5xx`,
  zaman aşımı. Bunlarda Auth görevi bekletir, üstel geri çekilmeyle (1 dk → en çok 6 sa) yeniden dener.
- **5 saniye** içinde yanıt ver. Veri çoksa: her çağrı silebildiği kadarını tek işlemde silsin, iş bitmediyse `503` dönsün;
  Auth tekrar çağırır, kalan silinir, sonunda `204`. `202` dönüp arka planda silmek **yasak**.
- Yanıt gövdesi okunmaz; gövde dönme. Token'ı, hesap verisini loglama (en fazla `accountId` + sonuç).
- Saklama istisnası (ör. append-only denetim kaydı) yalnız hukuki gerekçeyle, kişiyi tanımlamayan biçimde (aktör REF'i
  anonimleşir) ve **Auth sahibiyle birlikte** kararlaştırılır; servis belgesine yazılır. `204` "kişisel veri kalmadı" demektir.
- Outbox'ta bekleyen ve `accountId`/kişisel veri taşıyan olaylar da silinir veya anonimleşir (Social böyle yaptı).

## 4. Uygulama (Spring Boot 4 / Security 7) — Social'ı örnek al

Main'de çalışan referans: `backend/services/social-service` (PR #8). Dosyalar: `infrastructure/config/JwtConfig.java`,
`infrastructure/config/SecurityConfig.java`, `api/controller/AccountErasureController.java`, `api/controller/InternalApiPaths.java`,
`application/service/AccountErasureService.java`, test `api/controller/AccountErasureTest.java`. **Ama §4.4'teki iki ek denetimi
de yap** (Social'da henüz yok).

### 4.1 Özellik

```properties
# Yeni ENV değil; sabit, servisin kendi adı.
ltz.<servis>.auth.service-audience=<servis>-service
```

### 4.2 İkinci decoder (servis token'ı)

Kullanıcı token'ı decoder'ın aynen kalır. Yanına ikinci bir decoder: aynı JWKS, `validateType(false)` + açık `JwtTypeValidator`.

```java
public static OAuth2TokenValidator<Jwt> serviceTokenValidator(String issuer, String serviceAudience, Clock clock) {
    List<OAuth2TokenValidator<Jwt>> rules = new ArrayList<>(
            commonRules(issuer, serviceAudience, clock, TokenClaims.SERVICE_TOKEN_TYPE)); // typ=svc+jwt, iss, aud, exp, iat, sub
    rules.add(new JwtClaimValidator<String>(JwtClaimNames.SUB,
            subject -> subject != null && subject.startsWith(TokenClaims.SERVICE_SUBJECT_PREFIX)));
    rules.add(new JwtClaimValidator<Object>(JwtClaimNames.JTI, Objects::nonNull));
    rules.add(maxLifetime(Duration.ofSeconds(60), clock)); // exp-iat ≤ 60 sn VE exp ≤ şimdi+60 sn (+sapma)
    return new DelegatingOAuth2TokenValidator<>(rules);
}
```

`maxLifetime`'ı Social'dan aynen al: Spring, `iat` yoksa kendiliğinden `exp - 1 sn` atar; bu yüzden yalnız `exp - iat` yetmez,
bitiş zamanı da şimdiden en fazla 60 sn ileride olmalı.

### 4.3 Güvenlik zinciri: token türü yola göre seçilir

```java
.authorizeHttpRequests(authorize -> authorize
        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
        .requestMatchers(PUBLIC_ENDPOINTS).permitAll()
        .requestMatchers(PATHS.matcher(HttpMethod.DELETE, InternalApiPaths.ACCOUNT_ERASURE))
                .hasAuthority("SCOPE_" + TokenClaims.ACCOUNT_ERASE_SCOPE)
        .requestMatchers(AUTHENTICATED_ENDPOINTS).authenticated()
        .anyRequest().denyAll())
.oauth2ResourceServer(resourceServer -> resourceServer
        .authenticationManagerResolver(
                request -> INTERNAL_ENDPOINTS.matches(request) ? serviceTokens : userTokens))
```

Böylece kullanıcı token'ı iç uçta, servis token'ı `/api/v1/<servis>/**` uçlarında geçmez. Servis token'ı için authority
dönüştürücü yalnız `scope` → `SCOPE_*` üretir; `roles` okunmaz.

### 4.4 Controller: `sub` tam eşleşme + `account_id` = yol (Social'da EKSİK olan iki kural)

```java
@DeleteMapping(InternalApiPaths.ACCOUNT_ERASURE) // "/internal/v1/accounts/{accountId}"
@ResponseStatus(HttpStatus.NO_CONTENT)
public void erase(@PathVariable UUID accountId, @AuthenticationPrincipal Jwt token) {
    if (!TokenClaims.AUTH_SERVICE_SUBJECT.equals(token.getSubject())                    // "svc:auth-service"
            || !accountId.toString().equals(token.getClaimAsString(TokenClaims.ACCOUNT_ID))) { // "account_id"
        throw new AccessDeniedException("Service token is not valid for this erasure");
    }
    erasure.erase(accountId);
}
```

`AccessDeniedException`, `@RestControllerAdvice` üzerinden `403` ProblemDetail döner (Social'ın `GlobalExceptionHandler`'ı
böyle). Bu iki kural decoder'a değil buraya konur: decoder ileride başka servis çağrıcılarına da hizmet edebilir, ama **silme**
yalnız Auth'un ve yalnız token'ın bağlandığı hesap içindir.

### 4.5 Servis katmanı

- Tek `@Transactional` içinde o hesabın tüm kişisel verisini sil / anonimleştir (sahip olunan satırlar, ilişki satırları, outbox).
- Başka hesapların verisine dokunma (ör. arkadaşlıkta karşı tarafın satırı yalnız ilişki olarak gider).
- Silinecek bir şey yoksa hata değil, normal dönüş (`204`).
- Başka servise çağrı yapma; yalnız kendi DB'n.

## 5. Zorunlu testler (Social'ın `AccountErasureTest`'i şablon; + yeni 4 test)

| # | Senaryo | Beklenen |
| --- | --- | --- |
| 1 | Geçerli servis token'ı, verisi olan hesap | `204`, veri gerçekten silinmiş (DB'den kontrol) |
| 2 | Aynı çağrı ikinci kez | `204` |
| 3 | Hiç verisi olmayan hesap | `204` |
| 4 | Başka hesapların verisi | Dokunulmamış |
| 5 | Token yok | `401` |
| 6 | Kullanıcı token'ı (`at+jwt`, `aud=ltz-api`) | `401` |
| 7 | `aud` başka servis (ör. `social-service`) | `401` |
| 8 | `scope` yok / farklı | `403` |
| 9 | **`sub=svc:admin-service`** (yeni) | `403` |
| 10 | **`account_id` ≠ yoldaki id** (yeni) | `403` |
| 11 | **`account_id` yok** (yeni) | `403` |
| 12 | `exp - iat > 60 sn`, süresi dolmuş, `jti` yok, `iat` yok | `401` |
| 13 | HS256 / `alg=none` / bilinmeyen `kid` | `401` |
| 14 | Servis token'ı `/api/v1/<servis>/**` ucunda | `401` |
| 15 | `GET /internal/v1/accounts/{id}` veya başka `/internal/**` | `403` (denyAll) |
| 16 | Geçersiz UUID yolu | `400` |
| 17 | **Uyumluluk** (yeni): Auth'un gerçek `ServiceTokenIssuer`'ının ürettiği biçimde token | `204` |

Test token'larını Auth'un biçimiyle üret: başlık `typ=svc+jwt`, `kid`; claim'ler `iss=ltz-auth`, `aud=[<servis>-service]`,
`sub=svc:auth-service`, `scope=account:erase`, `account_id`, `iat/nbf/exp` (30 sn), `jti`. Gerçek anahtar/token teste yazılmaz;
test içinde geçici RSA anahtarı üret (Social'ın `IntegrationTestSupport`'u gibi).

## 6. Bitti sayılması için

1. Uç + iki ek denetim + 17 test yeşil; servis kapsamı ≥ %80.
2. `docs/api.md` içinde **kendi servisinin** başlığı altına kayıt: Durum, Sorumlu, Tüketici = `auth-service`, Endpoint,
   Yetkilendirme (servis token'ı, `scope=account:erase`), Response `204`, Hata Durumları (`400/401/403`), Bağlantı, Test Durumu.
3. `.\pre-push\pre-push.cmd service` (veya `full dev`) → `SAFE TO COMMIT`.
4. Silme istisnası (saklanan veri) varsa servis belgende gerekçesiyle yazılı ve Kerem'le kararlaştırılmış.
5. Kerem'e haber ver: dev'de bir test hesabı silinip o servisin görevinin `CONFIRMED` olduğu birlikte kontrol edilir.

## 7. Yapma

- `/internal/**` için Gateway route'u ekleme; iç uca kullanıcı token'ı kabul etme.
- `202` dönme; yönlendirme (`3xx`) dönme; uzun süren işi arka plana atıp `204` dönme.
- Token'ı, `Authorization` başlığını veya kişisel veriyi loglama.
- Auth'tan "biz farklı `aud`/`sub` bekliyoruz" diye değişiklik isteme: değer Auth'ta sabittir, sen ona uyarsın.
- Auth'un kodunu, DB'sini veya ortak Security ayarını kendi servisin için değiştirme.
