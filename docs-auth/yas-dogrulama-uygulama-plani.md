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
| K8 | Geri alma: yalnız kullanıcı kendisi. Yönetici ucu şimdilik yok (`revoked_by` kolonu migration'sız ileride hazır) |
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
- [x] **A4** Backend testleri (`AccountAgeVerificationTest` 14, `AdultVerificationServiceAgeTest` 2, `FlywayMigrationTest` V66; auth-service `verify` 637 test yeşil). Sapma: aralık dışı tarih (`INVALID_BIRTH_DATE`) deneme hakkı harcamaz; gövdedeki bilinmeyen alan `400`.
- [x] **A5** `docs/api.md` AUTH, `servis-sozlesmeleri.md`, `migration-yazim-kurallari.md`, `docs/decisions/migrations.md`.
- [x] **A6** Auth frontend: `reload()`, `AccountAgeSection`, `auth-api`, mesajlar, testler.
- [x] **A7** User: `docs-user/decisions/kararlar.md` notu, isteğe bağlı sözleşme testi, `AccountSettings` birleştirmesi.
- [ ] **A8** Docker ile uçtan uca doğrulama (`node ops/dev.mjs --all --force`, ana klondan).

## Sözleşme (Game ve Review'ın dayanağı)

- Claim: `adult_verified` (boolean). **Yalnız `true` iken yazılır**; yokluğu "doğrulanmadı" demektir. Okuyucular yalnız boolean `true`'ya yetki verir.
- Yalnız kullanıcı access token'ında (`at+jwt`); servis token'ında (`svc+jwt`) yazılmaz.
- Doğrulama sonrası iddia bir sonraki yenilemede gelir (≤60 sn); frontend hemen `POST /session/refresh` çağırır. Geri almada eski token ≤60 sn yetkili kalır (`ver` artırılmaz).
- Adı değişirse üç yerde birlikte: Auth `TokenClaims`, Game sabiti, `docs/api.md`.

## NOT VERIFIED / riskler

- Beyan zayıf kanıttır; sınır hesap başınadır, yeni hesapla aşılabilir.
- `GET /games` yanıtının Gateway veya aracı önbellekte kullanıcıdan bağımsız tutulup tutulmadığı doğrulanmadı (Game/Gateway sahibi).
- Review frontend'inin `/games` yanıtındaki yeni alanı tolere ettiği doğrulanmadı (Hamza).
- Gateway GET isteklerinde CSRF istemiyor (`CookieBearerFilter`, kod okundu, canlı denenmedi).
- Game ve Review ile uçtan uca senaryolar: diğer taraflar bitince.
