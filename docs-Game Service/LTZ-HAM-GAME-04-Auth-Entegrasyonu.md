# LTZ-HAM-GAME-04 — Auth Service entegrasyonu (geçici doğrulama altyapısı)

Tarih: 2026-10-21 · Sorumlu: Hamza · Servis: game-service

> **Durum: Auth Service entegrasyonu TAMAMLANMADI.** Auth Service (Kerem) henüz JWT üretmiyor. Game Service, Auth'tan bağımsız çalışan, yapılandırılabilir bir JWT resource-server altyapısı içerir. Bu altyapı **gerçek ortamda kullanılabilir değildir**: imza kaynağı tanımlanmadıkça hiçbir token kabul edilmez ve korumalı uçlar 401 döner. Bu belgedeki "doğrulandı" ifadeleri yalnızca Game Service'in kendi davranışı içindir; Auth ile uyum **NOT VERIFIED**'dir.

## 1. Neyin geçici, neyin kalıcı olduğu

| Parça | Durum | Auth hazır olunca |
| --- | --- | --- |
| `Actor` / `ActorRole` (kimlik ve rol modeli) | Kalıcı | Değişmez |
| Controller'lar, servisler, DTO'lar, DB şeması (V38–V40) | Kalıcı | Değişmez |
| `GameAccessPolicy` (token rolü + oyun bazlı yetki) | Kalıcı | Değişmez |
| `GameSecurityConfiguration` (rol–yol eşlemesi, fail-closed) | Kalıcı iskelet | Yalnız Auth'un rol adları/yolları değişirse |
| `JwtSecurityProperties` + `JwtDecoderConfiguration` | Yapılandırmalı | Gerçek JWKS/issuer/audience **değerleri** verilir |
| `LtzJwtAuthenticationConverter` (claim → Actor/rol) | **Tek değişim noktası** | Claim adı/biçimi farklıysa yalnız burası veya özellikler güncellenir |
| Test anahtarı ve test token'ları | Yalnız test kodu | Gerçek Auth entegrasyon testiyle tamamlanır |

Auth entegrasyonu için controller, servis, DTO veya veritabanı şeması yeniden tasarlanmaz.

## 2. Sorumlulukların ayrımı

| Sorumluluk | Sınıf (`infrastructure/security`) |
| --- | --- |
| JWT doğrulama (imza, algoritma allowlist, süre, issuer, audience, `sub`=UUID) | `JwtDecoderConfiguration`; imza kaynağı yoksa `FailClosedJwtDecoder` |
| Claim çözümleme ve rol dönüşümü | `LtzJwtAuthenticationConverter` → `ActorAuthentication` |
| Endpoint yetkilendirme (rol–yol) | `GameSecurityConfiguration` (`/admin/**` ADMIN, `/moderation/**` ADMIN+MODERATOR, `/developer/**` GAME_DEVELOPER, diğer her şey `denyAll`) |
| Aktörün controller'a verilmesi | `CurrentActorArgumentResolver` (controller JWT claim'i okumaz) |
| Oyun bazlı yetki (sahiplik) | `GameAccessPolicy` (`application/service`) — rol **ve** `game_developer_grants` kaydı ayrı ayrı |

Kural: Controller ve iş mantığı JWT claim'ine dokunmaz; kimlik yalnız `Actor` parametresiyle gelir.

## 3. Fail-closed davranışı

- `ltz.security.jwt.jwk-set-uri` veya `ltz.security.jwt.public-key` tanımlı değilse tüm token'lar reddedilir (401). Herkese açık katalog uçları etkilenmez.
- İmza kaynağı verilmiş ama `issuer`/`audience` eksikse, ikisi birden verilmişse, algoritma `RS256/RS384/RS512` dışındaysa, anahtar bozuksa veya JWKS adresi http(s) değilse **uygulama başlamaz** (yanlış yapılandırma açık bırakmaz).
- Başlık dışında kimlik kabul edilmez: çerez, query parametresi, `X-User-Id`/`X-Role` başlıkları yok sayılır. Kimlik yalnız `Authorization: Bearer` ile gelir.
- `alg=none`, yanlış imza, yanlış issuer/audience, süresi dolmuş, `exp` yok, `nbf` gelecekte, `sub` UUID değil → 401 (testli).
- Bilinmeyen roller yok sayılır; rolsüz token korumalı uçlarda 403 alır.

## 4. Yapılandırma özellikleri

Hepsi Spring özellikleridir (`application.properties` veya ortamdan). **Varsayılan boştur (fail-closed).** Kodda sabit değer, anahtar veya token yoktur.

| Özellik | Anlam | Varsayılan |
| --- | --- | --- |
| `ltz.security.jwt.jwk-set-uri` | Auth Service JWKS adresi (`public-key` ile birlikte verilemez) | boş |
| `ltz.security.jwt.public-key` | PEM X.509 RSA açık anahtarı (gizli değildir) | boş |
| `ltz.security.jwt.issuer` | Beklenen `iss` (imza kaynağı varsa zorunlu) | boş |
| `ltz.security.jwt.audience` | Beklenen `aud` (imza kaynağı varsa zorunlu) | boş |
| `ltz.security.jwt.algorithm` | `RS256`/`RS384`/`RS512` | `RS256` |
| `ltz.security.jwt.user-id-claim` | Kullanıcı UUID claim'i | `sub` |
| `ltz.security.jwt.roles-claim` | Rol claim'i (dizi veya boşluk/virgül ayrımlı metin; `ROLE_` öneki ve büyük/küçük harf yok sayılır) | `roles` |
| `ltz.security.jwt.clock-skew` | Süre toleransı | `60s` |
| `ltz.game.media.image-hosts` / `video-hosts` / `demo-hosts` | Medya bağlantısı host allowlist'i (tam host) | bkz. `application.properties` |

### ENV bağlama önerisi — ONAY BEKLİYOR (hiçbir `.env*`, `.env.example` veya `docker-compose.yml` değiştirilmedi)

SECURITY.md §1 gereği yeni ENV anahtarı için önce onay gerekir. Auth hazır olduğunda önerilen eşleme:

```properties
# application.properties (game-service)
ltz.security.jwt.issuer=${JWT_ISSUER:}            # mevcut anahtar (ör. ltz-auth)
ltz.security.jwt.audience=${JWT_AUDIENCE:}        # YENİ
ltz.security.jwt.jwk-set-uri=${JWT_JWK_SET_URI:}  # YENİ (Auth Service JWKS)
```

```dotenv
# .env.example (öneri)
JWT_AUDIENCE=
JWT_JWK_SET_URI=
```

`docker-compose.yml` içinde yalnız game-service ortamına bu üç anahtarın aktarılması gerekir (imzalama **özel** anahtarı Game Service'e hiçbir zaman verilmez; yalnız doğrulama).

## 5. Auth Service ile doğrulanması gereken sözleşme (Kerem)

Aşağıdakiler Game Service tarafında **varsayımdır**; Auth gerçek sözleşmesini yayınlayınca teyit edilmelidir:

1. `sub` claim'i kullanıcı UUID'sidir (`accounts.id`).
2. `iss` değeri (ör. `ltz-auth`) ve `aud` değeri (Game Service için hedef kitle adı).
3. İmza algoritması ve anahtar dağıtımı: RS256 + JWKS adresi mi, yoksa başka bir yöntem mi?
4. Rol claim'inin adı ve biçimi (`roles` dizisi varsayıldı) ve rol kodlarının `ADMIN`, `MODERATOR`, `GAME_DEVELOPER` ile birebir aynı olduğu (database.md §3.1: PLAYER, CONTENT_CREATOR, GAME_DEVELOPER, MODERATOR, ADMIN).
5. Token taşıma yöntemi: Game Service yalnız `Authorization: Bearer` okur. SECURITY.md §3 `[HEDEF]` HttpOnly çerez öngörüyor. **Çerez tabanlı oturuma geçilirse** özel bir `BearerTokenResolver` gerekir ve **CSRF tasarımı yeniden yapılmalıdır** (bugünkü yapı çerez kimlik bilgisi kabul etmediği için CSRF'ye karşı bir ambient yetki yoktur). Gateway'in `Authorization` başlığını koruyup korumadığı da doğrulanmalıdır.
6. Hesap durumu (`SUSPENDED`/`BANNED`/`DISABLED`) etkisi: Game Service durumu sorgulamaz; yalnız kısa ömürlü access token'a (varsayılan 900 sn) güvenir. İptalin yayılma süresi ve gerekirse iptal olayı (`AccountStatusChanged`) kararı Auth ile netleşmelidir.
7. `GAME_DEVELOPER` rolünün hangi kullanıcıya verildiği Auth'un konusudur. Game Service, yetki kaydı verirken (`POST /admin/games/{id}/developers`) kullanıcının bu role sahip olduğunu **doğrulayamaz**; kullanım anında token rolü ayrıca zorunludur.

## 6. Entegrasyon sırasında yapılacaklar (kontrol listesi)

- [ ] Auth sözleşmesini (yukarıdaki 1–7) teyit et; farklıysa yalnız `JwtSecurityProperties` değerlerini ve gerekirse `LtzJwtAuthenticationConverter`'ı güncelle.
- [ ] Yeni ENV anahtarları için onay al; `.env.example` ve `docker-compose.yml` (yalnız game-service) güncelle.
- [ ] Gerçek Auth tokenı ile entegrasyon testi ekle (test ortamı Auth'u ya da sözleşme testi); mevcut testlerin "yalnız Game Service davranışını doğrular" notunu güncelle.
- [ ] Gateway: `Authorization` başlığının iletildiğini, client'tan gelen iç kimlik başlıklarının temizlendiğini doğrula (architecture.md §4 önerisi hâlâ NOT IMPLEMENTED).
- [ ] Rol–yol eşlemesini Auth rol listesiyle karşılaştır (ör. MODERATOR'ın ADMIN uçlarına erişimi bilinçli olarak yok).
- [ ] 20/20 yetkisiz erişim hedefi (H-12) için tüm korumalı uçları gerçek token ile 401/403 testinden geçir.
- [ ] Çerez kararı verilirse CSRF tasarımı ve testini yeniden yap (bkz. 5.5).
- [ ] Yönetim eylemleri için Admin Service ile sözleşme gerekiyorsa (FR-ADM-07) ayrı karar: şimdilik Game Service kendi append-only `game_admin_audits` kaydını tutar.

## 7. NOT IMPLEMENTED / NOT VERIFIED

- Auth Service ile uyum: **NOT VERIFIED**. Gerçek token ile entegrasyon testi: **NOT IMPLEMENTED**.
- Rate limit (SECURITY.md §12): herkese açık toplu kart ucunda uygulandı (istemci başına, 429). Oluşturma, yetki, moderasyon ve diğer uçlarda yok: **NOT IMPLEMENTED**. Gateway'den istemci IP iletimi onay bekliyor (bkz. Faz 4 raporu).
- Hesap durumu/iptal denetimi: **NOT IMPLEMENTED** (yalnız kısa token ömrü).
- Servisler arası kimlik modeli: karar bekliyor; batch ucu bu yüzden bilinçli olarak herkese açıktır ve yalnız herkese açık veri döndürür.
- Gateway'de CORS/JWT ön doğrulama/istek limiti: **NOT IMPLEMENTED** (Game Service'in sınırında yeniden doğrulama vardır).
- Moderasyon sonucunun içerik sahibine bildirilmesi (notification-service): **NOT IMPLEMENTED**.

## 8. ADR taslağı önerisi (`docs/decisions/NNNN-game-service-jwt-dogrulama.md`)

> **Bağlam:** Auth Service JWT üretmiyor; Game Service korumalı yönetim uçları gerektiriyor.
> **Karar (geçici):** Her servis JWT'yi kendisi doğrular (resource server). Doğrulama yapılandırılabilir ve varsayılan fail-closed'dır. Kimlik/rol çözümleme tek bir dönüştürücüdedir; iş mantığı claim okumaz.
> **Sonuç:** Auth gerçek sözleşmesini yayınlayınca yalnız yapılandırma ve dönüştürücü değişir. Çerez kararı CSRF tasarımını yeniden açar.
> **Durum:** Taslak — ekip ve Kerem onayı bekliyor. Bu dosya `docs/decisions/` altına **eklenmedi**; önerilmiştir.
