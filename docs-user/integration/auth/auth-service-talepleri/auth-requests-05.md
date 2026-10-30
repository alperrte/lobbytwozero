# USER-AUTH-REQ-05 — Oturumlu hesaba Steam bağlama/kaldırma ve `ExternalIdentity*` olayları (T4)

Tarih: 2026-10-30. Gönderen: Kerem / user-service. Hedef: Kerem / auth-service.
Öncelik: MEDIUM (Faz 3 kullanıcıya yansımaz, User tarafı hazır). Durum: OPEN — Auth geliştirmesi gerekir.

**Gerekçe:** Auth'ta bugün yalnız Steam ile giriş/kayıt var (`/api/v1/auth/oauth/steam/{start,callback,complete}`); oturumdaki
hesaba Steam **bağlama** ve **kaldırma** akışı ile bunların olayı yok. Kimlik kanıtı yalnız Auth'ta üretilir
(`docs-auth/servis-sozlesmeleri.md` §1), User bunu kendisi yapamaz.

## İstenen

1. **Bağlama:** oturumlu kullanıcı, mevcut hesabına Steam kimliğini bağlayabilsin (Steam OpenID doğrulaması Auth'ta).
   Aynı SteamID64 başka bir hesapta bağlıysa reddedilsin (tekillik Auth'ta uygulansın).
2. **Kaldırma:** oturumlu kullanıcı bağlantıyı kaldırabilsin. **Son giriş yöntemi koruması** Auth'ta: Steam hesabın tek giriş
   yöntemiyse kaldırma reddedilsin.
3. **Olaylar** (outbox, `AUTH_EVENT_TARGETS=user`, alıcı `POST /internal/events`, `events:ingest`, `sub=svc:auth-service`,
   `account_id=aggregateId`, `eventVersion=1`):
   - `ExternalIdentityLinked`: `{ "accountId": "UUID = aggregateId", "provider": "STEAM", "providerSubject": "<17 haneli SteamID64>" }`
   - `ExternalIdentityUnlinked`: `{ "accountId": "UUID = aggregateId", "provider": "STEAM" }`
4. **Mevcut Steam hesapları:** daha önce Steam ile kayıt olmuş ACTIVE hesaplar için tek seferlik `ExternalIdentityLinked` yayını
   (REQ-02'deki B kararı gibi DEV'de hesaplar sıfırlanacaksa gerekmez; gerçek hesap varsa ayrıca konuşuruz).

## User tarafı (hazır)

- Alıcı: `ExternalIdentityLinked/Unlinked` işleniyor; son-durum upsert `occurredAt` ile korunuyor; tanınmayan `provider` yok
  sayılıyor (Auth ileride yeni sağlayıcı eklerse User kırılmaz); geçersiz SteamID ya da başka hesapta etkin kimlik `400`.
- Kullanıcı uçları: `GET /api/v1/user/me/linked-accounts`, `PATCH .../linked-accounts/steam` (`showOnProfile`).
- Sözleşme: `docs/api.md` → "USER — Bağlı hesaplar: Steam".

## Kabul

Bağlama sonrası `GET /api/v1/user/me/linked-accounts` yanıtında `linked=true` ve doğru `externalId`; kaldırma sonrası `linked=false`.
Uçtan uca doğrulama **NOT VERIFIED**.
