# Yaş doğrulaması: Auth uygulama planı ve durum

Sorumlu: Kerem (auth-service, User'daki küçük iş dahil). Başlangıç: 2026-11-12.
Kaynak belgeler (Hamza, repo dışı): `01-AUTH-SERVICE.md`, `02-USER-SERVICE.md`, `03-GAME-SERVICE.md`, `04-REVIEW-SERVICE.md`.

## Amaç ve mimari

Game'deki `includeAdult=true` bugün herkese açık. Hedef: +18 oyunları yalnız yaşı doğrulanmış, oturumlu kullanıcıya sunmak ve bunu sunucuda zorunlu kılmak.

```
Auth: yaş sonucunu saklar ──► access token'a `adult_verified` yazar (≤60 sn gecikme)
Game: token'dan okur, `includeAdult`'ı yalnız `Boolean.TRUE` ise uygular   (Hamza)
Review: yalnız `includeAdult` gönderir, kapı Game'dedir                    (Hamza)
User: değişiklik yok; yaş verisi tutmaz ve döndürmez
```

Game ve Review frontend'leri Auth'tan şunları bekler: `GET /auth/me` → `adultVerified` ve `useSession().reload()`. Zincirin tıkanabileceği yer burasıdır.

## Kararlar

| # | Karar |
| --- | --- |
| K1 | Beyan (`method = SELF_DECLARED`); üçüncü taraf sonradan `method` arkasında eklenir. Arayüz "doğrulandı" değil "beyan edildi" der |
| K2 | Yaş sonucunun sahibi Auth |
| K3 | Sınır 18 (tam 18. yaş günü 18 sayılır); hukuk teyit etmeli |
| K6 | Yalnız sonuç saklanır (`verified_at`, `method`, `revoked_at`); doğum tarihi hesaplanır ve atılır |
| K7 | Yalnız `trusted` hesap doğrulayabilir (aksi `403 STEP_UP_REQUIRED`) |
| K8 | Geri alma: kullanıcı kendisi ya da yönetici (B3, V67: kim ve neden saklanır). Yönetici geri alması kullanıcının yeniden beyanını engeller (`AGE_VERIFICATION_BLOCKED`); yönetici `lift-block` ile kaldırır |
| K9 | Hesap başına 24 saatte 3 deneme; başarılı doğrulama sayacı sıfırlar |

Hukuk/KVKK onayı koda değil yayına bağlı şarttır; aydınlatma metni güncellenmeli.

## Kodu okurken çıkan, kaynak belgelerde olmayan bulgular

1. **Tüketici derleme kümesi.** Game ve Hardware testleri Auth'un `AccessTokenIssuer` ve `TokenClaims` kaynağını doğrudan derler (`ProductionAuthSupport`); `ConsumerContractTest` `issue(Account, AuthSession, Instant)` imzasını kilitler. Mevcut 3 ve 5 parametreli `issue` değişmez; yeni aşırı yükleme eklenir, parametresi düz `boolean`dır.
2. **Deneme sınırı global.** `AttemptThrottleService.reserve(keyHash)` ayarları `AttemptProperties`'ten gelir (parola ve 2FA ile ortak). K9 için sayı ve pencereyi parametre alan aşırı yükleme eklenir.
3. **`security_events.type` kısıtı yok.** V61'de `type varchar(64)`, CHECK yok; V62–V65'te tabloya dokunulmuyor. Yeni olay türleri için `DROP/ADD CONSTRAINT` gerekmez.

## Aşamalar

Durum: [ ] yapılmadı · [~] sürüyor · [x] bitti. Her aşama sonunda commit mesajı önerilir (commit/push kullanıcıdadır).

- [x] **A0** Bu belge.
- [x] **A1** `V66__age_verification.sql` (`account_age_verifications`), entity, repository, `SecurityEventType` (`AGE_VERIFIED`, `AGE_VERIFICATION_REVOKED`, `AGE_VERIFICATION_REJECTED`), `AuthErrorCode.AGE_NOT_ELIGIBLE`.
- [x] **A2** `TokenClaims.ADULT_VERIFIED`, `AccessTokenIssuer` aşırı yüklemesi (yalnız `true` iken claim yazılır), `SessionService.issueTokens` bağlantısı.
- [x] **A3** `AdultVerificationService`, `AttemptThrottleService` aşırı yüklemesi, `AccountAgeController` (`POST` ve `DELETE /api/v1/auth/account/age-verification`), `/me` → `adultVerified`, `SecurityConfig`/`AuthApiPaths`.
- [x] **A4** Backend testleri (`AccountAgeVerificationTest` 14, `AdultVerificationServiceAgeTest` 2, `FlywayMigrationTest` V66; auth-service `verify` 637 test yeşil). Aralık dışı tarih (`INVALID_BIRTH_DATE`) bir deneme sayılır (belge §5.1.3 ile uyumlu, sonradan hizalandı); gövdedeki bilinmeyen alan `400`.
- [x] **A5** `docs/api.md` AUTH, `servis-sozlesmeleri.md`, `migration-yazim-kurallari.md`, `docs/decisions/migrations.md`.
- [x] **A6** Auth frontend: `reload()`, `AccountAgeSection`, `auth-api`, mesajlar, testler.
- [x] **A7** User: `docs-user/decisions/kararlar.md` notu, `AccountSettings` birleştirmesi (Auth tarafında). `NoAgeDataInResponsesTest` (user-service): yanıt tiplerinde yaş/doğum tarihi alanı yok.
- [x] **A9** Yönetici geri alma (B3): `V67__age_verification_admin_revocation.sql`, `POST /admin/accounts/{id}/age-verification/revoke` ve `/lift-block`, `AGE_VERIFICATION_BLOCKED`, olay `AGE_VERIFICATION_BLOCK_LIFTED`, testler. Hamza'nın belgelerinde yok; kararı Kerem verdi. Yönetici ekranı (frontend) yapılmadı: yalnız API.
- [x] **A8** Docker ile uçtan uca doğrulama, yerel `ltz-dev` yığını (postgres, mailpit, mail, auth, user, api-gateway), Gateway `:5080` üzerinden, 2026-11-12: 26/26 senaryo geçti. V66 uygulandı (`65 → 66`); kayıt, e-posta kodu, giriş, TOTP; `STEP_UP_REQUIRED`; beyan → `/me adultVerified=true` → refresh sonrası token'da `adult_verified: true` (refresh öncesi yok); geri alma ve yeniden beyan; 3 denemeden sonra `429` (kilitliyken doğru tarih de `429`); gelecek/bozuk tarih `400`. Ortak (paylaşılan) DEV sunucusunda doğrulanmadı.

## Sözleşme (Game ve Review'ın dayanağı)

- Claim: `adult_verified` (boolean). **Yalnız `true` iken yazılır**; yokluğu "doğrulanmadı" demektir. Okuyucular yalnız boolean `true`'ya yetki verir.
- Yalnız kullanıcı access token'ında (`at+jwt`); servis token'ında (`svc+jwt`) yazılmaz.
- Doğrulama sonrası iddia bir sonraki yenilemede gelir (≤60 sn); frontend hemen `POST /session/refresh` çağırır. Geri almada eski token ≤60 sn yetkili kalır (`ver` artırılmaz).
- Adı değişirse üç yerde birlikte: Auth `TokenClaims`, Game sabiti, `docs/api.md`.

## NOT VERIFIED / riskler

- Beyan zayıf kanıttır; sınır hesap başınadır, yeni hesapla aşılabilir.
- `GET /games` yanıtının Gateway veya aracı önbellekte kullanıcıdan bağımsız tutulup tutulmadığı doğrulanmadı (Game/Gateway sahibi).
- Review frontend'inin `/games` yanıtındaki yeni alanı tolere ettiği doğrulanmadı (Hamza).
- Gateway çerezi `Bearer`'a çevirip claim'i iletiyor ve `GET /me` çerezle CSRF başlığı olmadan çalışıyor (A8'de canlı denendi). Başka servislerin GET uçlarında aynı davranış denenmedi.
- Oturumsuz isteğin kimlik kontrolüyle (401) reddedildiği canlıda ayrıca denenmedi; A8'deki oturumsuz deneme CSRF yüzünden 403 aldı (401 garantisi backend testlerinde).
- Dev yığınında güvenli çerez modu açık: çerez adları `__Host-` önekli (`__Host-XSRF-TOKEN` vb.). Tarayıcıda `http://localhost` ile giriş denemesi ayrıca doğrulanmadı.
- Game ve Review ile uçtan uca senaryolar: diğer taraflar bitince.
