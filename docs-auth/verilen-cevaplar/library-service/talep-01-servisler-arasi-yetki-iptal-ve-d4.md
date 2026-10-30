# TALEP 01 — SERVİSLER ARASI YETKİ, OTURUM İPTALİ VE D4 DOĞRULAMASI

**Kimden:** Hamza (library-service) · **Kime:** Kerem (auth-service) · **Talep tarihi:** 2026-10-30 · **Cevap tarihi:** 2026-10-30
**Durum:** CEVAPLANDI. Kararlar Kerem tarafından verildi; Auth tarafı kodlandı (`feature/auth-service`, main'e PR ile girer). Karşı uçlar (User, Social, Library) bekleniyor.
**Library:** 15/18 kalır. `LIB-10`, `LIB-11`, `LIB-14` bu cevapla kapanmaz.

---

## 1. KAYNAK KODUNDAN DOĞRULANAN AUTH GERÇEKLERİ

| Library'nin gözlemi | Koddaki durum |
|---|---|
| Kullanıcı token'ı RS256, `typ=at+jwt`, `iss=ltz-auth`, `aud=ltz-api`, `sub` hesap UUID'si, `sid`/`ver`/`roles`/`jti` | **Doğru.** `docs/api.md` "Access token" |
| JWKS `GET /api/v1/auth/.well-known/jwks.json` | **Doğru.** |
| Oturum durumu yalnız Auth uçlarında denetlenir; diğer servislere introspection/iptal ucu yok | **Doğru.** `SessionStateValidator` yalnız auth-service'te çalışır. Ayrı bir iptal ya da sorgu ucu yok |
| Düz servis token'ı: `POST /internal/v1/service-tokens`, kapsamlar `events:ingest` ve `blocks:check`, kullanıcı/hedef bağı yok | **30 Ekim'e kadar doğruydu.** Bu cevapla `privacy:read` (User Service) ve hesaba bağlı `signals:read` eklendi (§4) |
| `library-service`, `user-service`, `recommendation-service` istemci kaydı yok | **Doğruydu.** Üçü eklendi; gizli anahtar boşsa kapalı (fail-closed) |
| D4: `account:erase` tek hesaba bağlı kısa ömürlü token, 14 gün bekleme | **Doğru.** `ServiceTokenIssuer` 30 sn, `account_id`, `sub=svc:auth-service`. Bekleme `ltz.auth.deletion.grace-period=P14D` |
| Dev token'ı 900 sn, prod 60 sn | **Kısmen.** Kodda, Compose'da ve `.env.example`'da varsayılan **60 sn** (`JWT_ACCESS_TTL_SECONDS`, izinli aralık 60–900 sn). 900, yerel `.env`'den geliyor olmalı (**NOT VERIFIED**: `.env` okunmadı). `ops/generate-auth-dev-keys.mjs` yalnız tam `300` değerini 60'a çeker, 900'e dokunmaz |

`account:erase` hiçbir istemciye verilemez: `ServiceClientConfig.ALLOWED_TARGET_SCOPES` içinde yok, açılış denetimi reddeder (`ServiceClientConfigTest`).

## 2. DÖRT AKIŞIN DURUMU

| Akış | Auth | Bekleyen |
|---|---|---|
| **A** User → Library (LIB-10) | **MEVCUT.** Auth değişikliği gerekmez (karar K1) | User'ın Library'yi kullanıcı token'ıyla çağırması; Library'nin izin kararını C ile alması |
| **B** Recommendation → Library (LIB-14) | **AUTH HAZIR** (`signals:read`, hesaba bağlı) | Library sinyal ucu, User izin kararı, Recommendation istemcisi |
| **C** Library → User (LIB-11) | **AUTH HAZIR** (`privacy:read`) | User Service ucu hazır: `POST /internal/v1/privacy/decisions`. Gerçek Auth token'ıyla uçtan uca NOT VERIFIED |
| **D** User → Social | **GEREKSİZ (karar değişti).** User Service kimseyi çağırmıyor; Social verisi olayla gelir (`FriendshipChanged`, `UserBlocked`, `UserUnblocked`). `user-service` istemcisi ve `friends:check` kapsamı kaldırıldı | Social: olayları User'a göndermek (`social-service` → `user-service`, `events:ingest`, Auth'ta hazır) |

## 3. KARARLAR (KEREM, 2026-10-30)

| # | Karar | Gerekçe |
|---|---|---|
| K1 | **A:** Auth, görüntüleyeni taşıyan delegasyon/exchange token'ı üretmez. User, kullanıcı isteğinde o kullanıcının kendi `at+jwt`'sini Library'ye iletir; Library `viewer`'ı yalnız `sub`'dan okur | Servis token'ından kullanıcı yetkisi türetilmez. Token 60 sn yaşar ve kullanıcının kendi yetkisini aşmaz. Yeni Auth yüzeyi açılmaz |
| K2 | **B:** Hesaba bağlı servis token'ı. `aud=library-service`, `scope=signals:read`, tek `account_id`, 60 sn; hesap `ACTIVE` olmalı. Kullanıcı JWT'si iletilmez | Kişisel veri okuyan kapsam tek hesaba bağlanır; çalınan token yalnız o hesabı okur |
| K3 | **C:** Düz servis token'ı. `aud=user-service`, `scope=privacy:read` (User Service'in hazır ucunun kapsamı; ilk önerdiğimiz `access:decide` bırakıldı), kullanıcı bağı yok | Library viewer'ı kendi doğrular; karar User'ındır |
| K4 | **D (değişti):** `user-service` istemcisi ve `friends:check` yok. User Service Social verisini olayla alır | User dış servisi senkron çağırmaz, döngü oluşmaz; gereksiz yetki verilmedi |
| K5 | **İptal penceresi değişmez:** 60 sn token + 30 sn saat toleransı = en çok 90 sn. Introspection, iptal listesi ve logout olayı yok | Game, Hardware ve Community ile aynı kabul. Library okuması salt-okunur |
| K6 | Dev ve test ortamı da **60 sn** çalışır | 900 sn ile yapılan iptal testi prod'u temsil etmez |
| K7 | **D4 kapanış kanıtı:** sözleşme testi ve izole gerçek Auth+Library E2E (`AUTH_DELETION_GRACE_PERIOD=PT0S`, sentetik hesap). Gerçek 14 gün **NOT VERIFIED** kalır; üretimde süre atlatılmaz | Hardware talep-03 ile aynı yöntem |

**Auth'un üstlenmediği şeyler:** Kullanıcı izni, gizlilik/görünürlük kararı ve hedef hesabın meşruiyeti. Bunlar User ve Social'ın işi. `signals:read`'deki `account_id` çağıranın beyanıdır. Auth yalnız hesabın var ve `ACTIVE` olduğunu denetler; izni kanıtlamaz.

## 4. KESİN SÖZLEŞME (LIBRARY'NİN UYACAĞI)

### 4.1 Library'nin aldığı token: Library → User gizlilik kararı (C)

```http
POST {AUTH_SERVICE_URL}/internal/v1/service-tokens
Content-Type: application/json

{ "clientId": "library-service", "clientSecret": "<AUTH_LIBRARY_SERVICE_CLIENT_SECRET>", "audience": "user-service" }
```

```json
{ "accessToken": "…", "tokenType": "Bearer", "expiresIn": 60, "scope": "events:ingest privacy:read" }
```

Token: `typ=svc+jwt`, `aud=user-service`, `sub=svc:library-service`, `scope=events:ingest privacy:read`, 60 sn, `account_id` yok. `privacy:read` User'ın `POST /internal/v1/privacy/decisions` ucu içindir; `events:ingest` Library'nin User'a göndereceği `LibrarySummaryChanged` olayı içindir. Yanıtta `Cache-Control: no-store` gelir; token yalnız süresi dolmadan yeniden kullanılır.

**User'ın karar ucu ve gövdesi User'ın tasarımıdır** (henüz yok). Library'nin fail-closed kuralı:
- Token alınamazsa, 401/403, zaman aşımı, 5xx ya da tanınmayan karar gelirse sonuç **erişilemez** sayılır.
- Kararı uzun süre önbelleğe alma. Önerilen tavan: token ömrü kadar (60 sn).

### 4.2 Library'nin kabul ettiği token: Recommendation → Library sinyal okuma (B)

Recommendation token'ı şöyle alır:

```json
{ "clientId": "recommendation-service", "clientSecret": "<…>", "audience": "library-service", "accountId": "<hesap UUID>" }
```

Library'nin sinyal ucunda **hepsi zorunlu** denetimler:

| Alan | Beklenen |
|---|---|
| `typ` (başlık) | `svc+jwt` (`at+jwt` reddedilir) |
| `alg`, `kid` | `RS256`, JWKS |
| `iss` | `ltz-auth` |
| `aud` | yalnız `library-service` |
| `sub` | tam `svc:recommendation-service` |
| `scope` | `signals:read` içermeli |
| `account_id` | istekteki hesapla **eşit**; değilse `403` |
| `exp - iat` | ≤ 60 sn, saat sapması ≤ 30 sn |

Bunlardan sonra Library, o hesabın sinyal izni için User'a sorar (4.1). İzin yoksa ya da karar alınamazsa veri dönmez.

**Uç biçimi Library'nin kararıdır.** Önerim: `GET /internal/v1/accounts/{accountId}/signals`, tek hesap, yalnız izinli alanlar, `/internal/**` Gateway'e açılmaz. Hata semantiği: `401` geçersiz token, `403` kapsam, hesap ya da izin yok. Varlık sızmasın diye izin yok ile hesap yok aynı kodu alır.

### 4.3 Kullanıcı adına çağrı (A)

Library'nin public uçları değişmez: kullanıcı JWT'si doğrulaması, `viewer = sub`, hedef yoldan gelir, gizlilik kararı 4.1 ile alınır. User'ın iletilen token'ı saklamaması, loglamaması ve arka planda kullanmaması User'ın sorumluluğudur.

### 4.4 Etkili olma

- Auth tarafı: bu dal main'e girince.
- Ortam: `AUTH_LIBRARY_SERVICE_CLIENT_SECRET`. `node ops/generate-auth-dev-keys.mjs` boşsa üretir. Aynı değer library-service ortamına verilir. Prod'da ayrı üretilir.

## 5. LOGOUT, İPTAL VE TOKEN ÖMRÜ

- **Politika bilinçli ve dokümante:** `docs/api.md` "Token iptali ve hesap durumu yayılımı (politika)", `acik-guvenlik-borclari.md` B2. Auth kendi uçlarında anında reddeder; diğer servisler JWKS ile yerel doğrular.
- **En kötü pencere:** prod 60 + 30 = **90 sn**. Dev'de 900 sn'yle 930 sn olur; bu yüzden K6.
- **Olay:** `AccountStatusChanged` yalnız yönetici durum değişikliğinde çıkar. Logout ve oturum iptali olay üretmez. Library isterse alıcı ucunu yazar, `library` olay hedefine eklenir; bu iptal süresini kısaltmaz, yalnız durum bilgisi verir.
- **Canlı doğrulama testi (Library):** Dev 60 sn iken logout et. Library ≤ 90 sn içinde reddetmeli; Auth hemen reddetmeli. Saat ileri alınmış token, `nbf` ve `exp` sınırlarını kontrol et.

## 6. D4 GERÇEK E2E PLANI

**Zincir:**
1. `AccountDeletionService`: onay geldiğinde her hedef için görev açar (`next_attempt_at = şimdi + grace-period`).
2. `AccountDeletionWorker` (`@Scheduled`, `ltz.auth.deletion.poll-interval`) koordinatörü çağırır.
3. `AccountDeletionCoordinator.processDue()`: vadesi gelen görevi kiralar, Auth PII'sini anonimleştirir.
4. `HttpAccountDataEraser`: `DELETE /internal/v1/accounts/{id}`, `svc+jwt`, `scope=account:erase`, `account_id`. 204 gelirse görev `CONFIRMED`, aksi halde geri çekilir.

| Aşama | Durum |
|---|---|
| (a) Sözleşme testi | **TAMAM.** Auth: `ServiceTokenIssuerTest`, `AccountDeletionServiceErasureTest`. Library: kendi HTTP testi |
| Bekleme davranışı (Auth içi) | **TAMAM.** `AccountDeletionGraceTest`: testte süre `PT0S`; 14 gün, görev zamanları kaydırılarak taklit edilir |
| (b) İzole gerçek Auth + Library | **NOT VERIFIED.** Yöntem aşağıda |
| (c) Gerçek 14 gün | **NOT VERIFIED.** Üretimde atlatılmaz |

**(b) için güvenli yöntem:**
1. Ayrı veritabanı.
2. Auth ortamında `AUTH_DELETION_GRACE_PERIOD=PT0S`, `LIBRARY_SERVICE_URL` gerçek Library.
3. Gerçek kişisel verisi olmayan bir sentetik hesap.
4. Library'de o hesaba ve ikinci bir kontrol hesabına satır yazılır.
5. Silme istenir ve onaylanır.

**Kapanış kanıtı:**
- Library görevi `CONFIRMED`.
- Library'de yalnız hedef hesabın satırları yok; **kontrol hesabının satırları duruyor**.
- İkinci çağrı da 204.
- Ucu olmayan hedefler `PENDING`, hesap `DELETION_REQUESTED`.

## 7. KİMİN NE YAPACAĞI

| Sahip | İş |
|---|---|
| **Auth (Kerem)** | Yapıldı: User Service için dört çağıran (social, library, hardware, review), recommendation → library hesaba bağlı kapsam, testler, `docs/api.md` ve `servis-sozlesmeleri.md` §3.5. Kalan: main'e PR |
| **Library (Hamza)** | (1) 4.1 gizlilik kararı istemcisi (`POST /internal/v1/privacy/decisions`), fail-closed; `LibrarySummaryChanged` olayını `events:ingest` ile User'a gönder. (2) 4.2 sinyal ucu ve doğrulayıcı (`account_id` eşitliği). (3) Dev `JWT_ACCESS_TTL_SECONDS=60` ile iptal testinin tekrarı. (4) D4 (b) ortak denemesi |
| **User (Kerem)** | Gizlilik karar ucu ve olay girişi hazır (`privacy:read`, `events:ingest`); kalan: gerçek Auth token'ıyla uçtan uca doğrulama ve Library'yi kullanıcı adına çağırma kuralları (4.3) |
| **Social (Umut)** | Arkadaşlık ve engel olaylarını User'a göndermek (`social-service` → `user-service`, `events:ingest`) ve gizlilik kararı için `privacy:read`. Arkadaşlık sorgu ucu gerekmez |
| **Recommendation (Alper)** | Arka plan işinde hesap başına token (`accountId`), kullanıcı JWT'si iletmeden |

**Sıra:**
1. User'ın karar API'si gelmeden C ve B kapanmaz.
2. Social ucu gelmeden D kapanmaz.
3. Library 4.2 doğrulayıcısını ve 4.1 fail-closed istemcisini şimdi yazabilir: sahte User ile (karar yok → erişilemez).

## 8. TEST DURUMU

| Seviye | Durum |
|---|---|
| Auth birim (`ServiceClientTokenServiceTest`, `ServiceClientConfigTest`) | PASS |
| Auth iç entegrasyon (`ServiceAudienceTokenTest`: gerçek uç, gerçek imza, gerçek DB) | PASS |
| Sahte HTTP: Library ↔ sahte User / sahte Auth | NOT VERIFIED (Library'nin işi) |
| Çok servisli gerçek E2E (B, C, D) | NOT VERIFIED (karşı uçlar yok) |
| D4 (b) | NOT VERIFIED |

## 9. AÇIKTA KALAN SORULARA KISA CEVAPLAR

**Değerlendirilip seçilmeyenler:**

| Seçenek | Neden seçilmedi |
|---|---|
| A için token exchange (görüntüleyeni taşıyan servis token'ı) | Servis token'ından kullanıcı yetkisi türetilir; yeni bir Auth yüzeyi açılır. Kullanıcının kendi token'ını iletmek aynı kanıtı yeni kural olmadan verir. İleride kullanıcı isteği olmadan viewer gerekirse yeniden konuşulur |
| B için hesaba bağlı olmayan düz `signals:read` | Çalınan token 60 sn boyunca her hesabı okur; hesaba bağlamak bunu tek hesaba indirir |
| B için kullanıcı JWT'sini Recommendation üzerinden Library'ye iletmek | Arka planda kullanıcı token'ı yok. Olsa da Recommendation'a kullanıcının tüm yetkisi geçer (`aud=ltz-api`) |
| Merkezi introspection / iptal listesi / logout olayı | Her istekte Auth bağımlılığı ya da tüm servislere yeni iş. 90 sn diğer ekiplerle kabul edilmiş sınır. Kritik yazma işlemi çıkarsa yeniden konuşulur |

**Kullanıcı kaynaklı istek ile arka plan işi aynı model mi? (B3)** Recommendation için evet, aynı model. Kullanıcı o an online olsa da olmasa da Library'yi hesaba bağlı `signals:read` token'ıyla çağırır, kullanıcı JWT'sini iletmez. Tek bir yol olduğu için Library tek doğrulayıcı yazar. Kullanıcının kendi token'ını iletmek (K1) yalnız A'da, kullanıcının o anki isteği içindir.

**Kim neye karar verir? (D3)** Auth: istemci, hedef, kapsam adı, token biçimi ve ömrü. Social: uçların biçimi, arkadaşlık sorgusunun içeriği, hangi çağıranı kabul edeceği. User: görünürlük kuralı (`PUBLIC`/`FRIENDS`), engel ve arkadaşlığı karara nasıl kattığı.

**Library'de değişmesi beklenen yerler** (main'deki koda göre; senin dalın farklıysa ona uyarla):
- `infrastructure/security/`: sinyal ucu için ayrı bir `svc+jwt` doğrulayıcısı. Silme ucunun doğrulayıcısını (`ServiceTokenDecoderConfiguration`, `AccountErasureSecurityConfiguration`) genişletme; o yalnız `svc:auth-service` ve `account:erase` içindir. Okuma ve silme ayrı kalır.
- `infrastructure/client/`: Auth token istemcisi ve User karar istemcisi (fail-closed).
- `api/controller/`: sinyal ucu.
- Ortam: `AUTH_LIBRARY_SERVICE_CLIENT_SECRET`, `USER_SERVICE_URL`.

## 10. RİSKLER

- `signals:read`'de `account_id` istemci beyanıdır. Recommendation'ın kendisi ele geçirilirse herhangi bir `ACTIVE` hesap için token alabilir. Bunu yalnız User izni ve Library denetimi sınırlar.
- K1'de User gerçek kullanıcı token'ı taşır; saklama ve loglama yasağının testi User'ın sorumluluğu.
- Logout ve oturum iptali diğer servislere 90 sn gecikmeli yansır; bu kabul edilen bir sınır.
- 900 sn'nin kaynağı doğrulanmadı.
