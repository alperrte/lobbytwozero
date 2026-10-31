# LIB-08 — Library → User gizlilik kararı kapsamı: `access:decide` → `privacy:read`

**Sahip:** Hamza · **Tarih:** 2026-10-31 · **Kaynak:** Kerem'in son kararı; Auth `origin/main` (PR #37, `5e1759e`)
**Durum:** LIB-10, LIB-11, LIB-14 **açık** (`[ ]`). Genel ilerleme **15/18 (%83,3)**.

## Sözleşme (Auth kodunda doğrulandı)
`library-service` → `user-service`: `POST /internal/v1/service-tokens` `{clientId, clientSecret, audience:"user-service"}` → `typ=svc+jwt`, `iss=ltz-auth`, `aud=user-service`, `sub=svc:library-service`, `scope="events:ingest privacy:read"`, 60 sn, `account_id` yok. `TokenClaims.PRIVACY_READ_SCOPE`, `application.properties` `library-service.targets.user-service=privacy:read,events:ingest`. `access:decide` Auth kaynağında kalmadı. Bu çalışma ağacındaki Auth yine eskidir (PR #36/#37 yok); gerçek doğrulama `origin/main` kaynağıyla geçici dizinde yapıldı.

## Library değişikliği
- `AuthServiceTokenClient`: `USER_AUDIENCE`, `PRIVACY_READ_SCOPE` ve `privacyDecisionToken()` eklendi. Gizlilik kararı için **yalnız `privacy:read`** aranır; yalnız `events:ingest`, yalnız eski `access:decide` veya ikisi birlikte yetmez. `privacy:read` ile birlikte `events:ingest` bulunması sorun değildir (kapsam kümesinde aranır). Önbellek, hedef ayrımı, yenileme, tür/süre kontrolü, retry yokluğu ve sır gizliliği değişmedi; çift kapsam/geri uyumluluk yok.
- `events:ingest`: Library'nin User'a olay gönderen bir mekanizması yok; eklenmedi (kapsam dışı). Yetki Auth'ta tanımlı ve token'da mevcuttur.
- Terminoloji: ilgili Library belgelerinde "erişim kararı" → "gizlilik kararı".
- Değişmeyenler: kullanıcı JWT, Recommendation `signals:read`+`account_id`, D4 `account:erase`, fail-closed `UnavailableLibraryVisibility`.

## Doğrulama
| Seviye | Sonuç | Gerçek / sahte |
|---|---|---|
| `mvn -Pci clean verify` | bkz. rapor (aşağıdaki özet) | gerçek PostgreSQL ve RSA/JWKS; Game, User sahte |
| `AuthServiceTokenClientTest` | 9 test: yalnız `events:ingest`, yalnız `access:decide`, ikisi birden, `signals:read`, `account:erase` vb. reddedilir; `privacy:read` (tek başına / `events:ingest` ile, her sırada) kabul | sahte HTTP |
| Java istemci ↔ çalışan gerçek Auth (PR #37) | 2/2 PASS | gerçek izole Auth |
| Auth'un gerçek token üreticisi ↔ Library HTTP/DB | 4/4 PASS | Auth kaynağı gerçek, User kararı sahte |
| İzole yığın E2E (token 60 sn, `PT0S`) | tokens 44/44, logout 9/9 (ilk ret exp+31 sn ≈ 91 sn), D4 12/12 | gerçek Auth ve Library; User yok |

Atlanan test bırakılmadı (merkezi kapı reddeder): Auth PR'ına bağlı gerçek-Auth testleri test ağacına eklenmedi; bu tablodaki gerçek Auth sonuçları o çalıştırmalardan alınmıştır.

## Kalan bağımlılıklar
- **User:** karar ucu Auth/Library'nin elindeki `origin/main`'de yok. `origin/feature/user-service` dalında `POST /internal/v1/privacy/decisions` (`PrivacyDecisionController`; `scope=privacy:read`, çağıran `svc:library-service` izinli; istek `viewerId`, `field`, `ownerIds`) bulunuyor ancak main'e girmedi ve Library adaptörü yazılmadı (talimat gereği şema tahmin edilmedi). Öneri: dal main'e girince şema okunarak ayrı bir görevde adaptör.
- **Recommendation:** `signals:read` istemcisi. **Social:** User'a olay/arkadaşlık verisi.
- **Ortam (onay gerekir):** `AUTH_LIBRARY_SERVICE_CLIENT_SECRET` (Auth ile aynı değer) library-service ortamına.
