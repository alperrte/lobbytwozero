# Auth Service Entegrasyon Rehberi (servis sahipleri için)

> Tarih: 2026-10-21 · Kapsam: Game, Library, Review, User, Mail, Admin, Gateway ve Auth'u tüketen diğer servisler.
> Bu belge **yeni bir standart tanımlamaz**; `docs/SECURITY.md`, `docs-auth/servis-sozlesmeleri.md` ve gerçek Auth koduyla
> uyumlu, servis sahipleri için kısa bir uygulama rehberidir. Çelişki olursa sıra: **Auth kodu > servis-sozlesmeleri.md > SECURITY.md > bu belge.**

## 0. Durum işaretleri

| İşaret | Anlamı |
| --- | --- |
| **[UYGULANDI]** | Auth kodunda var ve testlidir (dosya verilir). |
| **[SÖZLEŞME]** | Karar verilmiş/yazılmış ama kod yok; Auth veya karşı taraf yazmadı. |
| **[BEKLEYEN]** | Henüz karar veya geliştirme yok; bu alana dayanarak kod yazmayın. |
| **[ÖNERİ]** | Bu belgenin tavsiyesi; bağlayıcı değildir. |

## 1. Kanonik kaynaklar (tekrar yazılmaz)

| Konu | Tek kaynak |
| --- | --- |
| Genel güvenlik kuralları (IDOR, 401/403, cookie, CSRF, log) | `docs/SECURITY.md` §3–§4 |
| Servis sınırları ve servisler arası sözleşmeler (JWKS, silme, `UserRegistered`, D3/D4) | `docs-auth/servis-sozlesmeleri.md` |
| Auth endpoint'leri (istek/yanıt) | `docs/api.md` → `## AUTH` |
| Açık borçlar | `docs-auth/acik-guvenlik-borclari.md` |
| **Servis sahibinin yapacakları** | **Bu belge** |

## 2. Servis sahibinin kısa listesi

1. Access token'ı **kendiniz** doğrulayın (JWKS ile). Gateway'e veya `X-User-Id`/`X-Role` gibi başlıklara güvenmeyin (SECURITY.md §4).
2. Kullanıcı kimliğini **yalnız `sub` claim'inden** alın; istekteki `userId` alanını yok sayın/doğrulayın.
3. Rol kontrolünü `roles` claim'inden yapın; **sahiplik kontrolünü kendi veritabanınızda** yapın (§5).
4. Hesap durumu/iptal değişiklikleri size en fazla **access token ömrü kadar** geç ulaşır (§7).
5. Kişisel veri tutuyorsanız hesap silme çağrısını karşılayacak **idempotent iç uç** hazırlayın (§8). Auth bu çağrıyı D4 servis token'ıyla (`svc+jwt`) yapar; token'ın tüm alanlarını doğrulamak zorunludur (`servis-sozlesmeleri.md` §3).
6. Auth DB'sine erişmeyin; Auth'a yalnız bu belgedeki sözleşmelerle konuşun.

## 3. JWT doğrulaması

### 3.1 Anahtar ve uç nokta

| Konu | Değer | Durum |
| --- | --- | --- |
| JWKS | `GET /api/v1/auth/.well-known/jwks.json` (Gateway üzerinden veya `AUTH_SERVICE_URL` ile doğrudan), yalnız açık anahtar | **[UYGULANDI]** `JwksController` |
| Algoritma | **yalnız RS256**; `alg=none` ve HS256 reddedilmelidir | **[UYGULANDI]** (Auth kendi decoder'ında aynısını yapar) |
| `kid` | Açık anahtarın RFC 7638 parmak izi; JWT başlığında bulunur | **[UYGULANDI]** |
| Anahtar sayısı | **Tek aktif anahtar.** Anahtar rotasyonu/çoklu anahtar **yok**; anahtar değişirse `kid` değişir ve tüm token'lar geçersiz olur | **[BEKLEYEN]** (rehber N-1) |
| JWKS yanıtı önbellek başlığı | `Cache-Control: no-store` | **[UYGULANDI]** |

**[ÖNERİ]** JWKS'i süreç içinde önbelleğe alın (ör. 5–15 dk) ve **bilinmeyen `kid`** görünce bir kez yenileyip tekrar deneyin. Her istekte Auth'a gitmeyin.

### 3.2 Zorunlu doğrulamalar

| Alan | Beklenen değer | Not |
| --- | --- | --- |
| JWT başlığı `typ` | `at+jwt` | Auth'un kullanıcı token'ıdır. `svc+jwt` (servis kimliği, yalnız `/internal/**` uçları) kullanıcı uçlarında **reddedilmeli** |
| `iss` | `ltz-auth` (ENV `JWT_ISSUER`, varsayılan `ltz-auth`) | |
| `aud` | `ltz-api` içermeli (ENV `JWT_AUDIENCE`, varsayılan `ltz-api`) | Değer tüm ortamlarda Auth ile aynı olmalı |
| `exp`, `nbf`, `iat` | Zorunlu, süre/saat doğrulaması | Küçük saat sapması toleransı kullanın (≤ 60 sn) |
| `sub` | Zorunlu | Hesap kimliği (UUID metni) |
| İmza | Auth'un JWKS anahtarı | |

Bu liste Auth'un kendi `JwtConfig` doğrulayıcısıyla birebir aynıdır; farklı bir kural eklemeyin.

### 3.3 Claim'ler (gerçek token'dan, `AccessTokenIssuer`)

| Claim | Anlam | Tür |
| --- | --- | --- |
| `sub` | Hesap kimliği; **`userId` budur** | UUID metni |
| `roles` | Hesabın rolleri, öneksiz ad: `PLAYER`, `CONTENT_CREATOR`, `GAME_DEVELOPER`, `MODERATOR`, `ADMIN` | Metin listesi (sıralı) |
| `sid` | Oturum kimliği | UUID metni |
| `ver` | Hesabın `token_version` değeri | Sayı |
| `amr` | Kimlik doğrulama yöntemleri: `pwd`, `fed` (Google/Steam), `otp`, `rc` (kurtarma kodu) | Metin listesi |
| `jti` | Token kimliği | UUID metni |
| `iss`, `aud`, `iat`, `nbf`, `exp` | Standart | |

**Olmayan claim'ler (bunlara dayanmayın):** e-posta, kullanıcı adı, hesap durumu, `scope`, izin (permission) listesi, oyun bazlı yetki.
Profil bilgisi User Service'tedir.

**[UYGULANDI] Auth'a özgü iptal denetimi:** Auth, her kimlikli isteğinde `sid` (oturum aktif mi), hesap durumu ve `ver` değerini
veritabanıyla karşılaştırır (`SessionStateValidator`). **Diğer servislerde bu kontrol yoktur** (§7). `sid`/`ver` claim'lerini kendi başınıza
anlamlandırmaya çalışmayın; yalnız Auth bunları doğrulayabilir.

### 3.4 Spring örneği (**[ÖRNEK]** — ortak Security modülü gelince onun yapılandırması geçerlidir)

Aşağıdaki kod Auth'un `JwtConfig` yapısını tüketici tarafa uyarlar; yalnız standart Spring Security API'lerini kullanır.

```java
@Bean
JwtDecoder jwtDecoder(@Value("${ltz.auth.jwks-uri}") String jwksUri,
                      @Value("${ltz.auth.issuer}") String issuer,
                      @Value("${ltz.auth.audience}") String audience) {
    NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwksUri)
            .jwsAlgorithm(SignatureAlgorithm.RS256)
            .validateType(false)                       // typ aşağıda açıkça doğrulanır
            .build();
    decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
            new JwtTimestampValidator(Duration.ofSeconds(30)),
            new JwtIssuerValidator(issuer),
            new JwtTypeValidator("at+jwt"),
            new JwtClaimValidator<List<String>>(JwtClaimNames.AUD, aud -> aud != null && aud.contains(audience)),
            new JwtClaimValidator<Object>(JwtClaimNames.SUB, Objects::nonNull)));
    return decoder;
}

@Bean
JwtAuthenticationConverter jwtAuthenticationConverter() {
    JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
    authorities.setAuthoritiesClaimName("roles");
    authorities.setAuthorityPrefix("ROLE_");
    JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
    converter.setJwtGrantedAuthoritiesConverter(authorities);
    return converter;
}
```

Yeni bir ENV anahtarı gerekecekse (`jwks-uri` vb.) `docs/SECURITY.md` §1 uyarınca önce onay alın. JWKS adresi için mevcut `AUTH_SERVICE_URL` yeterlidir.

## 4. Roller ve endpoint yetkilendirmesi

Auth, **yalnız beş kanonik rolü** tanır: `PLAYER`, `CONTENT_CREATOR`, `GAME_DEVELOPER`, `MODERATOR`, `ADMIN` **[UYGULANDI]**.

- Kayıt olan herkes yalnız `PLAYER` olur. Diğer roller yalnız `ADMIN` tarafından verilir. `MODERATOR` ve `ADMIN` için TOTP (2FA) zorunludur.
- Rol kontrolü: `hasRole('ADMIN')`, `hasAnyRole('MODERATOR','ADMIN')` gibi. Rol listesi `roles` claim'inden gelir.
- Kural: **varsayılan reddet**. Her endpoint için açıkça `permitAll` veya rol belirtin (Auth'un `anyRequest().denyAll()` yaklaşımı).
- Rol, "ne yapabilir"i söyler; "**hangi kaynağa**" dokunabileceğini söylemez (§5).

## 5. JWT kimliği ile kaynak sahipliği

| Soru | Cevap veren | Nasıl |
| --- | --- | --- |
| Bu kişi kim? | JWT (`sub`) | İmza doğrulanmış token'dan |
| Bu rolde mi? | JWT (`roles`) | Token'dan; iptal gecikmesi §7 |
| **Bu kaynak bu kişinin mi?** | **Sizin servisiniz** | Kendi DB'nizde `owner_id = sub` kontrolü |
| Bu geliştirici bu **oyunu** yönetebilir mi? | **Game Service** | Auth oyun bazlı yetki **taşımaz** **[BEKLEYEN: Game'in kendi tablosu]** |

- Request body/query/header'dan gelen `userId` güvenilmez. Gerekirse yok sayın veya `sub` ile eşleşmediğinde reddedin.
- Sahibi olmayan kaynak için **404** (güvenli gizleme) veya **403** döndürebilirsiniz; SECURITY.md §4 hangisinin seçildiğini servisin kararına bırakır, tutarlı olun.
- `GAME_DEVELOPER` rolü, "bu kişi geliştirici hesabı"dır. Hangi oyunların geliştiricisi olduğu Game Service'in kendi verisidir (GAME-12: "Auth rolü ve oyun bazlı sahiplik ayrı ayrı kontrol edilir").

## 6. 401 ve 403

| Durum | Ne zaman | Gövde |
| --- | --- | --- |
| **401** | Token yok, imza/süre/`iss`/`aud`/`typ` hatalı | `ProblemDetail`; neden ayrıntısı verilmez |
| **403** | Token geçerli ama rol/sahiplik yetersiz | `ProblemDetail` |
| 404 | Kaynak yok veya güvenli gizleme | |

- Auth bunları `ProblemDetail` (RFC 9457) olarak döner (`ProblemDetailSecurityHandler` → `GlobalExceptionHandler`) **[UYGULANDI]**. Servisleriniz de aynı biçimi kullanmalıdır.
- Gövdeye token, claim değeri, stack trace, SQL veya iç adres koymayın.
- 401 alan istemci, refresh akışına yönlenir (Auth `POST /api/v1/auth/session/refresh`); bu akış **cookie** ile çalışır. Tarayıcıdan gelen istekte Gateway çerezi `Authorization: Bearer` başlığına çevirir **[UYGULANDI]** (§6.1); servisler yalnız Bearer görür. Gateway'i kullanmadan doğrudan servise vuran testlerde başlığı kendiniz verin.

### 6.1 Gateway ne yapar? (uygulanmış davranış)

| Davranış | Ayrıntı |
| --- | --- |
| Çerez → Bearer | `/api/v1/**` (Auth hariç) isteğinde `Authorization` yoksa ve erişim çerezi (`__Host-ltz_access` veya `ltz_access`) varsa Gateway `Authorization: Bearer <çerez>` ekler. İstekte zaten `Authorization` varsa dokunulmaz. |
| CSRF (çerezle gelen POST/PUT/PATCH/DELETE) | `X-XSRF-TOKEN` başlığı, `XSRF-TOKEN` çerezine eşit olmalı; değilse **403** `CSRF_TOKEN_INVALID`, istek servise ulaşmaz. Servisler Bearer isteklerini CSRF'den muaf tuttuğu için bu kontrol Gateway'de yapılır. |
| Origin | Çerezle gelen durum değiştiren isteklerde `Origin` varsa izinli listede olmalıdır. |
| CORS | `CORS_ALLOWED_ORIGINS` tam eşleşme (`*` yasak), `credentials=true`, başlıklar: `Content-Type, Authorization, X-XSRF-TOKEN, X-Request-Id, Accept`. |
| Başlık temizliği | İstemciden gelen `X-User-*`, `X-Account-*`, `X-Auth-*`, `X-Internal-*`, `X-Role(s)` her zaman silinir; `Forwarded`/`X-Forwarded-*`/`X-Real-IP` yalnız `GATEWAY_TRUSTED_PROXIES` ile eşleşen bağlantıdan korunur. |
| Hız sınırı | `/api/v1/auth/**` için IP başına dakikada `GATEWAY_AUTH_RATE_LIMIT_PER_MINUTE` (varsayılan 120; JWKS muaf); aşımda **429** + `Retry-After`. Sayaç Gateway örneğinin belleğindedir. |

Servis sahibi için sonuç: servisinize yalnız doğrulanmış bir Bearer (veya hiç kimlik) gelir; kimliği yine **kendiniz JWKS ile doğrulayın**, Gateway'e güvenmeyin (SECURITY.md §4).

## 7. Hesap durumu, token iptali ve gecikme sınırı

| Olay | Auth içinde | Sizin serviste |
| --- | --- | --- |
| Çıkış, tüm cihazlardan çıkış | Anında | Token `exp`'e kadar geçerli görünür |
| Parola değişimi/sıfırlama | Anında (oturumlar iptal, `ver` artar) | Aynı |
| Rol/durum değişikliği (askı, yasak, rol alma) | Anında | Aynı |
| Hesap silme isteği onayı | Anında | Aynı |

- **En fazla gecikme = access token ömrü.** Varsayılan **300 sn (5 dk)**, Auth'ta izin verilen aralık 1–15 dk (`JWT_ACCESS_TTL_SECONDS`).
  `docs/SECURITY.md` §3'teki `900` değeri eski bir örnektir; geçerli değer ENV'dedir.
- Hesap durumu claim'de **yoktur**. Askıdaki/yasaklı bir hesabın token'ı, süresi dolana kadar sizde geçerli görünür (B2/D3).
- `AccountStatusChanged` olayı ve introspection ucu **yoktur** **[BEKLEYEN, D3]**. Uydurmayın.
- **[ÖNERİ]** Geri alınamaz veya yüksek riskli işlemlerde (para, toplu silme) ek önlemi servis kendi tasarlasın; bu karar Auth sözleşmesi değildir.

## 8. Hesap silinmesi: veri sahibi servislerin sorumluluğu

**[SÖZLEŞME + Auth tarafı UYGULANDI]** (`servis-sozlesmeleri.md` §2 #6, §4):

- Auth, kullanıcı silmeyi onaylayınca her **hedef servis** için bir görev açar ve şu çağrıyı yapar:
  `DELETE <SERVİS>_SERVICE_URL/internal/v1/accounts/{accountId}` (idempotent).
- Servis, o hesaba ait kişisel veriyi **silmeyi/anonimleştirmeyi tamamladığında `204`** (veya `200`) döner. Zaten silinmişse veya verisi yoksa da
  `204` dönmelidir. **`202`, `3xx`, `404`, `401/403`, `5xx` ve zaman aşımı onay sayılmaz**; Auth yeniden dener (üstel geri çekilme). Tüm
  hedefler onaylayana kadar hesap `DELETED` olmaz.
- Servis kimliği (**D4**) **[UYGULANDI, Auth tarafı]**: her çağrıda yeni token; `typ=svc+jwt`, `iss=ltz-auth`, `aud=<servis>-service` (ör.
  `social-service`), `sub=svc:auth-service`, `scope=account:erase`, `account_id=<silinecek hesap>`, ömür 30 sn, `jti`. Hedef servis **hepsini**
  doğrular; özellikle `sub` **tam eşleşme** ve `account_id` **yoldaki `{accountId}` ile aynı** olmalıdır (aksi halde `403`). Ayrıntı ve tablo:
  `servis-sozlesmeleri.md` §3–§3.1. Uç kimliksiz açılmaz ve Gateway'e yayınlanmaz.
- Silme hedef listesi `ltz.auth.deletion.targets` ile yönetilir (16): `user, mail, game, library, review, social, messaging, lfp, community,
  content, event, hardware, recommendation, performance, admin, notification`. **`game` ve `admin` 2026-10-23'te eklendi (S2).** Hangi verinin
  silinip hangisinin hukuki gerekçeyle anonim olarak saklanacağı servis sahibi ve Auth sahibi tarafından birlikte kararlaştırılır.
- Auth "uygulanamaz" beyanını kabul etmez: **verisi olmayan servis de ucu açıp `204` döner.** Listeden çıkarma yalnız Auth sahibinin kararıdır.

## 9. Servisler arası çağrılar ve tamamlanmamış sözleşmeler

| Konu | Durum |
| --- | --- |
| JWKS ile kullanıcı JWT doğrulama | **[UYGULANDI]** (Auth tarafı); tüketici servislerde yok |
| Servis kimliği D4 (`svc+jwt`) | Auth → hedef servis (`account:erase`) **[UYGULANDI]**; Admin → Auth iç uç **[BEKLEYEN]** |
| Admin → Auth rol/durum | Şimdilik kullanıcı token'ıyla `/api/v1/auth/admin/**` (yalnız `ADMIN`); servis kimlikli uç **[BEKLEYEN]** |
| Auth → Mail (3 port: doğrulama, sıfırlama, silme onayı) | Port + dev (Mailpit) **[UYGULANDI]**; Mail Service ucu **[BEKLEYEN]** |
| Auth → User `UserRegistered` | **[BEKLEYEN]** (outbox/User Service yok) |
| Auth → servisler `AccountStatusChanged` | **[BEKLEYEN, D3]** |
| Gateway: CORS, cookie→Bearer + CSRF, başlık temizliği, Auth hız sınırı | **[UYGULANDI]** (`api-gateway`, §6.1) |
| Gateway: güvenilir proxy IP'si, çok örnekli ortak hız sınırı (Redis vb.) | Ayar var (`GATEWAY_TRUSTED_PROXIES`), ortak limitleyici **[BEKLEYEN]** |
| İlk ADMIN oluşturma yolu | **[BEKLEYEN, B7]** |

Mail Service sahibi: doğrulama/sıfırlama/silme token ve kodlarını **üretmez, loglamaz, kalıcı düz metin tutmaz** (servis-sozlesmeleri.md §1).
User Service sahibi: Auth'un parola, refresh token veya Steam kimlik kanıtını tutmaz/üretmez.

## 10. Game, Library, Review için somut örnekler

> Aşağıdakiler **Auth'un sağladığı** (kimlik, rol) ile **servisin kendisinin yapması gereken** (sahiplik, iş kuralı) işi ayırır.
> Görev numaraları `docs/servis-gereksinimleri/LTZ_Servis_Icerikleri_Hamza.md` içindendir.

### 10.1 Game Service (GAME-07, GAME-12, GAME-15)

| İş | Auth'tan gelen | Servisin yaptığı |
| --- | --- | --- |
| Oyun ekle/güncelle/arşivle (GAME-07) | `roles` içinde `ADMIN` (veya kararlaştırılan başka rol) | `hasRole` kuralı; işlem kaydı (GAME-26) `sub` ile |
| Geliştirici kendi oyununu yönetir (GAME-12) | `GAME_DEVELOPER` rolü | Oyun-geliştirici sahipliğini **Game DB'sinde** denetler; başkasının oyunu → 403/404 |
| Herkese açık katalog okuma (GAME-01/03) | — (misafir olabilir) | Bu uçlar `permitAll` ise açıkça belirtilmeli |

```java
@PutMapping("/games/{gameId}")
@PreAuthorize("hasAnyRole('ADMIN','GAME_DEVELOPER')")
ResponseEntity<GameResponse> update(@PathVariable UUID gameId, @AuthenticationPrincipal Jwt jwt, @RequestBody UpdateGame body) {
    UUID actor = UUID.fromString(jwt.getSubject());
    boolean admin = jwt.getClaimAsStringList("roles").contains("ADMIN");
    games.update(gameId, actor, admin, body);   // servis: admin değilse developer_grants(gameId, actor) kontrol eder
    return ResponseEntity.ok(...);
}
```

### 10.2 Library Service (LIB-12, LIB-13, LIB-18)

- `userId` her zaman `sub`'dan. İstek gövdesinde `userId` alanı **tanımlamayın** (LIB-13: istemcinin gönderdiği `userId` ile başkasının koleksiyonu değiştirilemez).
- Kayıt anahtarı `(userId, gameId)` benzersizdir (LIB-12); `gameId` doğrulaması Game Service ile yapılır.
- Başkasının koleksiyon öğesine erişim → 404 (veya 403), sızıntı olmadan. Gizli profil kuralını (LIB-18) kendi tablonuzda uygulayın.
- Hesap silme (§8): `library` hedef listesinde; kullanıcının koleksiyon/favori verisini silen idempotent iç uç gerekir.

### 10.3 Review Service (REV-02, REV-03, REV-04, REV-07)

- İnceleme oluşturma: `userId = sub`; başkası adına oluşturma imkânsız olmalı (REV-02).
- Düzenleme/silme: `review.userId == sub` zorunlu (REV-03/04). Moderatör/yönetici silmesi `hasAnyRole('MODERATOR','ADMIN')` ve **denetim kaydıyla** (SECURITY.md §4).
- Rapor akışı Admin Service ile sözleşmelidir (REV-07); Admin → Review çağrısı için servis kimliği D4 gelene kadar **[BEKLEYEN]**.
- Hesap silme: `review` hedef listesinde; inceleme/puan/beğeni verisi için silme/anonimleştirme kuralı (REV-04: içerik silme ile puan silme ayrımı) açıkça belirlenmeli.

## 11. Entegrasyon ve güvenlik test kontrol listesi

Her servis için (SECURITY.md hedefi: 20/20 yetkisiz erişim testi 401/403):

- [ ] Token yok → 401; bozuk imza → 401; süresi dolmuş → 401.
- [ ] `alg=none` ve HS256 ile imzalı token → 401.
- [ ] Yanlış `iss`, yanlış `aud`, `typ` ≠ `at+jwt` → 401.
- [ ] Geçerli token, yetersiz rol → 403.
- [ ] Başkasının kaynağı (IDOR/BOLA) → 404/403 ve veri sızmaz.
- [ ] Gövdede/başlıkta sahte `userId`/`X-User-Id` → yok sayılır.
- [ ] `PLAYER` token'ıyla yönetici uçları → 403.
- [ ] Bilinmeyen `kid` → JWKS bir kez yenilenir, sonra 401.
- [ ] Hata gövdesi `ProblemDetail`; token/stack trace/SQL yok; loglarda token ve kişisel veri yok.
- [ ] Silme ucu: aynı çağrı iki kez → ikisi de 2xx; kimliksiz çağrı → 401; veri gerçekten silindi/anonimleşti.
- [ ] Auth ile aynı `iss`/`aud` değerleri ortak ENV'den geliyor (hardcode yok).
- [ ] (Gateway hazır olunca) cookie ile gelen oturumda `Authorization: Bearer` servise ulaşıyor.

## 12. Bu belge hangi durumda güncellenir?

D3 (durum yayılımı), D4 (servis kimliği), Gateway cookie→Bearer, anahtar rotasyonu veya rol modeli değiştiğinde. Sözleşme değişimi önce
`servis-sozlesmeleri.md` ve gerekirse `docs/api.md` kayıtlarında yapılır; bu belge onlara uyum sağlar.

## 13. Production'a geçiş kontrol listesi (Auth + Gateway)

Aşağıdakiler gerçek `SPRING_PROFILES_ACTIVE=prod` denemesiyle doğrulandı: doğru ayarlarda **tek engel Mail Service adaptörüdür**; http URL
verilirse OAuth/bağlantı koruması servisi durdurur; `production`, `staging` gibi tanınmayan profil adı açılışta reddedilir.

| # | Ne | Durum |
| --- | --- | --- |
| 1 | `SPRING_PROFILES_ACTIVE=prod` (yalnız `dev`, `test`, `prod` kabul edilir) | Zorunlu |
| 2 | **Mail Service adaptörü.** Auth'ta `ltz.auth.mail.adapter` production-ready bir adaptöre geçmeden `prod` açılmaz | **BEKLEYEN** (Mail Service yok) |
| 3 | `AUTH_PUBLIC_BASE_URL`, `FRONTEND_PUBLIC_URL`, `CORS_ALLOWED_ORIGINS` yalnız `https` | Zorunlu (açılışta denetlenir) |
| 4 | Prod'a **yeni** `AUTH_JWT_PRIVATE_KEY`, `AUTH_TOTP_ENCRYPTION_KEY`, `AUTH_OTP_HMAC_KEY`; dev anahtarları kullanılmaz, sır yöneticisinde tutulur. JWT anahtarı: `openssl genrsa 3072 \| openssl pkcs8 -topk8 -nocrypt -outform DER \| base64 -w0` | Zorunlu |
| 5 | `AUTH_SECURE_COOKIES` ve `AUTH_2FA_REQUIRED_ROLES` ENV ile gevşetilemez (yalnız dev/test); ADMIN için 2FA zorunlu kalır | Kod ile korunur |
| 6 | Google ile giriş için `GOOGLE_CLIENT_ID/SECRET` ve Google Console'da `<AUTH_PUBLIC_BASE_URL>/api/v1/auth/oauth/google/callback` | Gerçek kimlik bilgisi gerekir |
| 7 | Gateway `GATEWAY_TRUSTED_PROXIES`: Gateway bir yük dengeleyicinin arkasındaysa onun adresi; aksi halde boş | Ortama göre |
| 8 | Gateway hız sınırı çok örnekli kurulumda örnek başınadır; ortak limitleyici (Redis vb.) | **BEKLEYEN** |
| 9 | Veritabanı: servis başına ayrı rol (`ltz_dev` superuser'ı prod'da kullanılmaz), güçlü `POSTGRES_PASSWORD` | Zorunlu |
| 10 | `SWAGGER_ENABLED=false`, Actuator yalnız `health` (varsayılan böyle) | Varsayılan |
| 11 | Servis kimliği (D4): Auth tarafı **UYGULANDI**; hedef servislerde silme ucu (16 servis) | **BEKLEYEN** (§8, §9) |
| 12 | Anahtar rotasyonu ve parola politikası (≥15 + kara liste) | **BEKLEYEN** (`acik-guvenlik-borclari.md` S9, S10) |
| 13 | Migration'lar V61–V64 donmuştur; şema değişikliği V65+ | Süreç kuralı |

