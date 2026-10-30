# LIB-07 — Library ↔ Auth: Kerem'in kesin kararlarına uyarlama (sonuç)

**Sahip:** Hamza · **Tarih:** 2026-10-31 · **Kaynak sözleşme:** `docs-auth/verilen-cevaplar/library-service/talep-01-servisler-arasi-yetki-iptal-ve-d4.md` (K1–K7; `origin/main`, PR #36)
**Durum:** LIB-10, LIB-11, LIB-14 **açık** (`[ ]`). Genel ilerleme **15/18**.

> **Güncelleme (2026-10-31, sonra):** Kerem'in son kararıyla Library → User kapsamı `access:decide` yerine **`privacy:read`** (+ ayrı olay yetkisi `events:ingest`) oldu; Auth `origin/main` (PR #37) bunu üretir ve `access:decide` vermez. Aşağıdaki `access:decide` ifadeleri o anki durumu anlatır; güncel sonuç `LTZ-HAM-LIB-08-Gizlilik-Karari-Kapsami-Guncellemesi.md` içindedir.

## 1. Auth sürümü ve belge tutarsızlıkları

- Bu çalışma ağacındaki Auth PR #36'dan **eskidir** (`ServiceClientTokenIssuer.issue(…, accountId)`, `access:decide`, `signals:read` yok). Auth'a ve diğer servislere dokunulmadı. Sözleşme `origin/main` kaynağından okundu; gerçek E2E için `origin/main` Auth'u geçici dizinde derlenip izole çalıştırıldı (repo dışı).
- **`privacy:read` ile `access:decide`:** Kerem'in belgesinde ve Auth kodunda `privacy:read` **hiç geçmez**; Library→User kapsamı her yerde **`access:decide`**dir (§3 K3, §4.1, §7; `TokenClaims.ACCESS_DECIDE_SCOPE`, `application.properties` `library-service.targets.user-service=access:decide`). Belge içinde tutarsızlık yoktur; istek metnindeki `privacy:read` ifadesi Auth'ta karşılıksızdır. Library'de hiçbir yerde `privacy:read` veya ikisini birden kabul eden gevşek yol yoktur. Yanıtta ek `events:ingest` de yoktur (kodda library→user hedefi yalnız `access:decide`).
- **User karar ucu:** `POST /internal/v1/privacy/decisions` **belgede de kodda da yoktur** (`origin/main` ve `origin/feature/user-service` tarandı: karar/gizlilik ucu yok). Belge de "henüz yok" der (§4.1, §7). İstek/yanıt şeması, token türü, hata davranışı **tanımsızdır**.
- **Social olayları (K4):** User–Social arası olay tabanlı akış User/Social'ın işidir; Library Social'a bağlanmaz, olay tüketicisi yazmaz.

## 2. Library'de yapılanlar

| Dosya | Değişiklik |
|---|---|
| `AccountErasureSecurityConfiguration` | Sinyal ucu yetkisi `SCOPE_signals:read` (eski `library:recommendation:read` kalktı) |
| `LibraryInternalReadController` | Sinyal ucunda `sub=svc:recommendation-service` ve **imzalı `account_id` == yoldaki hesap** (kanonik UUID); eksik/yanlış → 403. Eski `target_user_id` hesap yerine geçmez |
| `AuthServiceTokenClient` (+2 DTO) | Auth `POST /internal/v1/service-tokens` istemcisi (client credentials). Hedef başına önbellek, token bitmeden 10 sn önce yenileme, kapsam/süre/tür doğrulaması, retry yok, gizli değer loglanmaz. Başarısızlıkta "token yok" |
| `application.properties` | `ltz.library.auth.base-url/client-id/client-secret`; sır `AUTH_LIBRARY_SERVICE_CLIENT_SECRET` (boşsa istemci kapalı) |
| `LibraryApiTest` | Sinyal testleri `signals:read`/`account_id` sözleşmesine taşındı; çapraz token ve yanlış hesap senaryoları eklendi |
| `AuthServiceTokenClientTest` | 8 test (sahte HTTP) |
| `docs/api.md` (LIBRARY) | Sinyal kaydı yenilendi; profil iç ucu `DEPRECATED` (K1) |

**Değişmeyenler:** D4 silme güvenliği, kullanıcı JWT doğrulaması, public profil uçları, `UnavailableLibraryVisibility` (fail-closed).

## 3. Görevlerin durumu

- **LIB-10 (User → Library):** K1 gereği User kullanıcının kendi `at+jwt`'siyle mevcut public uçları çağırır; Library `viewer=sub`, ADMIN bypass'ı yok, karar alınamazsa 503. Library tarafı **hazırdı, değişmedi**. Açık kalma nedeni: User tüketicisi yok.
- **LIB-11 (Library → User):** Auth token istemcisi hazır ve gerçek Auth'a karşı doğrulandı. **User'a giden karar HTTP adaptörü yazılmadı**: karar ucunun şeması yok, tahminle uygulanmaz. Üretimde görünürlük hâlâ fail-closed (503). Açık kalma nedeni: User karar API'si.
- **LIB-14 (Recommendation → Library):** Library sinyal ucu `signals:read` + `account_id` sözleşmesine uyarlandı; gerçek Auth token'ıyla doğrulandı. Açık kalma nedeni: User izin kararı ve Recommendation istemcisi. İzin kararı alınamadığı için gerçek Library bugün sinyal **döndürmez** (503).

## 4. Doğrulama özeti (gerçek ↔ sahte)

| Seviye | Sonuç | Gerçek / sahte |
|---|---|---|
| Maven `clean verify -Pci` | 120 test, 0 hata, 0 atlanan, kapı PASS, satır kapsamı %98,54 | Gerçek PostgreSQL, gerçek RSA/JWKS; Game ve User **sahte** |
| Checkstyle/PMD ratchet | Yeni ihlal 0 | — |
| Auth'un gerçek `ServiceClientTokenIssuer`/`ServiceTokenIssuer` çıktısı ↔ Library'nin gerçek HTTP/DB'si | 4/4 PASS (`origin/main` Auth kaynağıyla) | Auth **kaynağı** gerçek, servis çalışmıyor; User kararı **sahte** |
| Java `AuthServiceTokenClient` ↔ gerçek çalışan Auth | 2/2 PASS | **Gerçek** izole Auth |
| İzole yığın E2E (`origin/main` Auth + çalışma ağacı Library + PostgreSQL, token 60 sn, silme bekleme `PT0S`) | tokens 42/42, logout 9/9, D4 12/12 | **Gerçek** Auth ve Library; User/Game/Recommendation **yok** |

Atlanan test bırakılmadı: merkezi kapı atlanan testi reddeder. Auth PR #36 kaynağına bağlı üç test (gerçek Auth token üreticisi ↔ Library, Java istemci ↔ çalışan Auth) bu yüzden test ağacına eklenmedi; izole yığın E2E betikleri de repoya alınmadı. Sonuçlar bu belgedeki tablodadır.

### 4.1 Logout ve 90 sn penceresi (K5/K6)
Efektif token ömrü izole Auth'ta 60 sn (`exp−iat=60`). Logout → Auth aynı token'ı hemen reddetti (401). Library token'ı logout anında kabul etti (200; introspection yok). İlk ret `exp+31 sn` ≈ logout'tan 91 sn sonra (yoklama aralığı 2 sn); son kabul `exp+29 sn`. Yani pencere 60+30 sn'dir, token süresi dolmadan kabul davranışı beklendiği gibidir. Merkezi introspection/iptal altyapısı eklenmedi.

### 4.2 D4 üç ayrı kanıt
1. **Sözleşme testi:** `AuthErasureAdapterIntegrationTest` (Auth'un gerçek HTTP adaptörü ↔ Library) ve `AccountErasureTest`/`LibraryApiTest`.
2. **İzole gerçek E2E:** sentetik hedef + kontrol hesabı; gerçek Auth silme akışı (`PT0S`) Library'yi gerçek D4 token'ıyla çağırdı; görev `CONFIRMED`, yalnız hedef hesabın satırları silindi, kontrol hesabının 3 satırı kaldı; ucu olmayan hedefler `PENDING`, hesap `DELETION_REQUESTED`; görev izole DB'de PENDING'e alınıp tekrar işlendi → ikinci çağrı da onaylandı (idempotent 204).
3. **Gerçek 14 günlük bekleme:** **NOT VERIFIED**; üretimde süre atlatılmaz (K7).

## 5. Kalan bağımlılıklar

| Servis | İş |
|---|---|
| **User (Kerem)** | Gizlilik karar API'si (`aud=user-service`, `scope=privacy:read`; şema, ALLOW/DENY ve hata kodları); kullanıcı JWT'sini ileten profil akışı; Social'dan olayla gelen engel/arkadaşlık verisi |
| **Social (Umut)** | `blocks/check` çağıranlarına `svc:user-service`; arkadaşlık sorgusu/olayı |
| **Recommendation (Alper)** | Hesap başına `accountId`'li `signals:read` token'ı alıp sinyal ucunu çağıran istemci |
| **Auth (Kerem)** | PR #36 main'de; ortam: `AUTH_LIBRARY_SERVICE_CLIENT_SECRET` (aynı değer Library ortamına) |
| **Library (Hamza)** | User karar şeması gelince `LibraryVisibilityPort` HTTP adaptörü (`AuthServiceTokenClient` + `access:decide`); ENV/Compose (aşağıda öneri) |

**ENV/Compose önerisi (uygulanmadı, onay gerekir):** library-service ortamına `AUTH_LIBRARY_SERVICE_CLIENT_SECRET` (Auth'taki ile aynı, `.env.example`'a boş giriş) ve ileride `USER_SERVICE_URL`. Sır tanımsızsa Library çalışmaya devam eder, görünürlük kararı 503 verir.

## 6. Checklist (15/18)

Faz 1: [x] LIB-01 [x] LIB-02 [x] LIB-12 [x] LIB-13 [x] LIB-15 · Faz 2: [x] LIB-03 [x] LIB-04 · Faz 3: [x] LIB-05 [x] LIB-06 [x] LIB-07 [x] LIB-08 [x] LIB-09 [x] LIB-16 · Faz 4: **[ ] LIB-10 [ ] LIB-11 [ ] LIB-14** [x] LIB-18 · Faz 5: [x] LIB-17

