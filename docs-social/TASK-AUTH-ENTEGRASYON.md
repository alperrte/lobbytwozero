# Social Service — Auth ve Gateway Entegrasyonu (TASK-AUTH-ENTEGRASYON)

> Tarih: 2026-10-23 · Sorumlu: Umut · Servis: `social-service` · Kaynak: `docs/AUTH_SERVICE_ENTEGRASYON_REHBERI.md` ve `docs/EKIP_AUTH_GATEWAY_ENTEGRASYON_NOTLARI.md` (§5.5 social) · **Auth-service ve api-gateway kodunda hiçbir değişiklik yapılmadı.**
>
> Bu belge numaralı bir PDA task'ı değildir; SOC-01..03'ün kimlik/yetki katmanını rehbere göre denetleyip tamamlar.

## 1. Rehber denetimi (§0.4) — adım adım

| # | Adım | Durum | Kanıt |
| --- | --- | --- | --- |
| 1 | JWT doğrulayıcı (RS256, JWKS, `iss`, `aud`, `typ=at+jwt`, `exp`) | **VAR** (SOC-01'den) | `JwtConfig.userTokenDecoder`, `JwtValidationTest` |
| 2 | `roles` claim'i → `ROLE_` yetkisi | **VAR** | `SecurityConfig.roleConverter` |
| 3 | Her endpoint'e açık kural, geri kalan `denyAll` | **VAR** | `SecurityConfig` (yalnız sağlık `permitAll`; `/api/v1/social/**` kimlik; `/internal/**` servis token'ı; gerisi `denyAll`) |
| 4 | Sahiplik (IDOR/BOLA); `userId` yalnız `sub` | **VAR** | `AuthenticatedUser`, IDOR testleri (istek, arkadaşlık, engel, kod) |
| 5 | Hesap silme ucu (veri tutuyorsa) | **YENİ — bu çalışmada** | `DELETE /internal/v1/accounts/{accountId}`, `AccountErasureTest` |
| 6 | 401/403/ProblemDetail | **VAR** | `GlobalExceptionHandler`; token/SQL/stack trace yok |
| 7 | Testler (§2 listesi) + gerçek Auth token'ı | **TAMAM** | 184 otomatik test + canlı uçtan uca deneme (§4) |
| 8 | `docs/api.md` `Yetkilendirme` alanları | **GÜNCEL** | SOCIAL kayıtları + yeni "Hesap verisi silme" kaydı |
| 9 | Rapor | Bu belge | |

Rehber §2 test listesi (her madde bir testle karşılanıyor):

| Madde | Test |
| --- | --- |
| Token yok → 401 | `JwtValidationTest.tokensizIstek401...`, `AccountErasureTest.tokensizCagri401...` |
| Bozuk / süresi dolmuş → 401 | `bozulmusImzaReddedilir`, `suresiDolmusTokenReddedilir`, `henuzGecerliOlmayanTokenReddedilir` |
| `alg=none` ve HS256 → 401 | `algoritmaKarisikligiReddedilir_noneVeHs256` (+ servis token'ı için `imzaSaldirilari401...`) |
| Yanlış `iss` / `aud` / `typ` → 401 | `yanlisAudienceIssuerVeTurReddedilir` (+ servis token'ı için `yanlisAudienceIssuerVeTur401`) |
| Yetersiz rol/kapsam → 403 | `AccountErasureTest.kapsamEksikVeyaFarkliysa403...`, bilinmeyen yol `403 FORBIDDEN` |
| Başkasının kaydı → 404/403 | `FriendRequestControllerTest`, `FriendshipControllerTest`, `BlockControllerTest` (IDOR) |
| Sahte `X-User-Id` yok sayılır | `FriendRequestControllerTest.istemciKimlikBasliklariYokSayilir` + canlı deneme |
| Bilinmeyen `kid` → 401, JWKS yenilenir | `bilinmeyenKidReddedilir_401`, `jwksAnahtarDonunceBilinmeyenKidIcinJwksYenilenir...` |
| Silme ucu iki kez → ikisi de 2xx | `silmeIdempotent_ikinciCagriVeOlmayanHesapDa204` |
| Hata gövdesinde token/SQL/stack trace yok | Tüm hata testleri `ProblemDetail` + `code`; değer geri yansıtılmaz |

## 2. Bu çalışmada eklenenler

**Hesap silme ucu** (rehber §0.4 adım 5, kural 9; social, Auth'un `ltz.auth.deletion.targets` listesinde):
- `DELETE /internal/v1/accounts/{accountId}` → `204`, **idempotent** (veri yoksa ya da ikinci çağrıda da `204`).
- Hesaba ait tüm Social verisini tek transaction'da siler: gönderdiği/aldığı istekler (her durumda), arkadaşlıklar, koyduğu ve kendisine konan engeller, davet kodları ve yükünde hesabın kimliği geçen outbox olayları. Başkalarının kendi verisine dokunmaz.
- **Kimliksiz açılmaz:** yalnız Auth'un imzaladığı servis token'ı kabul edilir (`docs-auth/servis-sozlesmeleri.md` §3): `typ=svc+jwt`, RS256/JWKS, `iss=ltz-auth`, `aud=social-service`, `sub=svc:…`, `jti`, `scope=account:erase`, ömür ≤ 60 sn.
- **Token türleri karışmaz:** yol bazlı iki ayrı doğrulayıcı vardır. Kullanıcı token'ı (hatta ADMIN rolüyle) iç uçta 401; servis token'ı kullanıcı uçlarında 401.
- **Gateway'e açılmaz:** Gateway yalnız `/api/v1/**` route eder; `/internal/**` canlıda `404` (doğrulandı).

**Doğrulayıcı sıkılaştırmaları**
- `iat` zorunluluğu eklendi (Auth'un doğrulayıcısıyla birebir). Not: Spring `iat` yoksa `exp-1` atadığı için bu kural tek başına `iat` varlığını kanıtlamaz; Auth'un kendi doğrulayıcısı da aynı davranışı gösterir.
- Servis token'ı ömrü için **`iat`'tan bağımsız** ek kural: bitiş zamanı şimdiden en fazla 60 sn (+30 sn sapma) ileride olmalı. Aksi halde `iat` çıkarılarak 60 sn kuralı aşılabilirdi (testle yakalandı ve kapatıldı).

**Yapılandırma:** ek ENV yok. Yalnız uygulama özelliği: `ltz.social.auth.service-audience=social-service` (sabit varsayılan).

## 3. Auth tarafından beklenenler (Kerem'e iletilecek — Auth'ta hiçbir şey değiştirilmedi)

| # | Konu | Durum |
| --- | --- | --- |
| 1 | Silme çağrısının gerçek adaptörü: Auth'ta bugün `UnavailableAccountDataEraser` var; hedef servislere RestClient ile `DELETE <SERVICE>_SERVICE_URL/internal/v1/accounts/{id}` çağıran adaptör yok | PENDING (Auth sahibi) |
| 2 | Servis token'ı (D4) üreticisi: Auth `svc+jwt` üretmiyor. Social'ın beklediği biçim `servis-sozlesmeleri.md` §3'teki sözleşmedir: `typ=svc+jwt`, `iss=ltz-auth`, **`aud=social-service`**, `sub=svc:<çağıran>`, `scope=account:erase`, `jti`, `exp ≤ 60 sn` | PENDING (Auth sahibi) |
| 3 | `aud` değeri: sözleşmede "hedef servis" yazıyor; Social `social-service` bekliyor (Mail için sözleşmede `mail-service` geçiyor). Auth farklı bir ad kullanacaksa `ltz.social.auth.service-audience` değeri değişir; **Kerem ile teyit edilmeli** | TEYİT BEKLİYOR |

Social tarafı hazır olduğundan Auth'ta yukarıdakiler yazılana kadar bu uç hiçbir yerden çağrılmaz; fail-closed (token yoksa 401).

## 4. Gerçek Auth ile canlı uçtan uca deneme (rehber §0.4 adım 7)

Compose'taki gerçek Auth + Gateway + Social ile: gerçek kayıt → Mailpit kodu → e-posta doğrulama → giriş → Gateway üzerinden Social. **26/26 kontrol geçti.**

- Gerçek token: `typ=at+jwt`, `alg=RS256`, `kid` var; claim'ler `amr, aud, exp, iat, iss, jti, nbf, roles, sid, sub, ver`. **`aud` düz metin** (dizi değil); doğrulayıcı hem metin hem dizi biçimini kabul eder (ikisi de testli).
- Gateway: gerçek çerezle `GET /friends` 200; çerezli `POST` + doğru `X-XSRF-TOKEN` 201 (gönderen = token `sub`); CSRF başlığı olmadan `POST` 403 ve istek servise ulaşmaz; kimliksiz 401.
- Servis: kendi isteğini kabul etmeye çalışan gönderen 404 (IDOR); alıcı kabul 200; tekrar istek 409; sahte `X-User-Id`/`X-Role` başlıkları yok sayıldı; bozuk imza 401.
- İç uç: Gateway üzerinden `404`; doğrudan servise tokensiz 401; **gerçek kullanıcı token'ıyla 401**; veri yerinde.
- Servis token'ıyla canlı silme **NOT VERIFIED** (Auth üreticisi yok); bu yol otomatik testlerle (kendi RSA anahtarıyla imzalanan sözleşme biçimli token'larla) doğrulandı.
- Denemede iki geliştirme hesabı oluşturuldu (`e2e.<rastgele>@example.test`); dev Auth veritabanında kalır. Hesap silme akışı (Auth tarafı) hazır olunca temizlenebilir.

## 5. Doğrulama durumu

| Kontrol | Sonuç |
| --- | --- |
| Birim + entegrasyon testleri (Testcontainers, gerçek PostgreSQL 17) | PASS — **184 test** (önceki 167 + yeni 17) |
| `.\pre-push\pre-push.cmd social-service` | PASS — `BACKEND TEST CHECK PASSED`, 184 test, satır kapsamı **%95,95** |
| `.\pre-push\pre-push.cmd full dev` / SonarQube | Yeniden koşulmadı: engel değişmedi (game-service migration testleri Windows CRLF — bkz. `TASK-02.md` §1) |
| Canlı uçtan uca (gerçek Auth, Gateway, Social) | PASS — 26/26 |

## 6. Açık noktalar

| # | Konu | Durum |
| --- | --- | --- |
| 1 | §3'teki Auth beklentileri (adaptör, `svc+jwt` üreticisi, `aud` teyidi) | Kerem |
| 2 | Hesap durumu/askı yayılımı (D3): token en fazla 5 dk geçerli görünür (rehber §7) | Auth'ta yok; Social'a özgü yapılacak iş yok |
| 3 | Kullanıcı varlık doğrulaması (silinen hesaba sonradan istek) | user-service ucu bekleniyor |
| 4 | Silinen hesabın arkadaşlarına `FriendshipChanged REMOVED` olayı | Bilerek yazılmıyor (yük silinen kimliği taşırdı); messaging kendi silme çağrısını Auth'tan alır |
| 5 | Gateway'de social için oran sınırı | NOT IMPLEMENTED |
| 6 | Sonar ve tam kapı | game-service CRLF engeli çözülünce |
