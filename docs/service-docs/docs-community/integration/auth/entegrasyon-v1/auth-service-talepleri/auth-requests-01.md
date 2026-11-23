# Auth Talebi 01 — Servisler arası olaylar için servis token'ı (`events:ingest`) ve giden çağrılar

> Tarih: 2026-10-27 · Gönderen: Nisa (community-service) · Muhatap: Kerem (Auth) · Durum: **CEVAPLANDI (2026-10-28), detay ve sonraki adım `auth-requests-04.md`'de**
> Not: Bu belge Auth Service'in yapısını değiştirme talebi değildir; Auth'un mevcut D4 sözleşmesini (`docs-auth/servis-sozlesmeleri.md` §3) **başka çağıranlara/kapsamlara genişletme** önerisidir. Karar ve uygulama Auth'un sahibindedir. Community, Auth'un vereceği her sözleşmeye uyar.

## 1. Bağlam (kısa)

Auth'un D4 servis token'ı bugün **tek bir iş** için üretiliyor: Auth → hedef servis, `scope=account:erase`, `sub=svc:auth-service`. Community bu sözleşmeye uydu: `DELETE /internal/v1/accounts/{accountId}` ucu hazır ve tüm alanlarını doğruluyor (bkz. `auth-integration-02.md`).

Community'nin **başka iki servisler arası akışı** var ve ikisi de aynı mekanizmaya (servis token'ı) muhtaç:

| # | Akış | Yön | Bugünkü durum |
| --- | --- | --- | --- |
| A | Moderasyon kararı (`ModerationActionTaken`) | admin-service → **community** `POST /internal/events` | Community ucu hazır; çağıranın kimliği için servis token'ı yok |
| B | Engelleme olayları (`UserBlocked`, `UserUnblocked`) | social-service → **community** `POST /internal/events` | Aynı |
| C | Rapor ve bildirim olayları (`ContentReported`, `CommentCreated`, `PostLiked`, `PostReposted`) | **community** → admin-service / notification-service `POST /internal/events` | Community outbox'a yazıyor; relay KAPALI, çünkü giden çağrıda gösterecek token yok |

## 2. İstenen

### 2.1 Gelen çağrılar için (A ve B) — kapsam ve çağıran kimliği

Auth'un, **admin-service** ve **social-service** için community'ye (ve ileride diğer servislere) çağrı yaparken kullanabilecekleri bir servis token'ı üretme yolu tanımlaması. Community'nin beklediği biçim (D4 ile aynı, yalnız çağıran ve kapsam farklı):

| Alan | Beklenen değer |
| --- | --- |
| `typ` / `alg` | `svc+jwt` / RS256 (aynı JWKS) |
| `iss` | `ltz-auth` |
| `aud` | `community-service` (tek değer) |
| `sub` | **`svc:<çağıran servis>`**, örn. `svc:admin-service`, `svc:social-service` |
| `scope` | **`events:ingest`** (adı önerimizdir; Auth farklı bir ad seçerse community uyar) |
| `jti`, `iat`, `nbf`, `exp` | D4 ile aynı (ömür ≤ 60 sn) |

Community bu token'la şunları denetler: D4'teki tüm ortak kurallar + `scope=events:ingest` + **`sub` = `svc:` + zarftaki `producer` alanı** (yani admin-service token'ı ile social-service adına olay gönderilemez). `ModerationActionTaken` yalnız `svc:admin-service`, `UserBlocked`/`UserUnblocked` yalnız `svc:social-service` token'ıyla kabul edilir.

### 2.2 Giden çağrılar için (C) — community token'ı nasıl edinir?

Community'nin admin ve notification servislerine çağrı yaparken token'ı **kimden ve nasıl** alacağı. İki seçenek önerimiz var; Auth tercih etsin:

1. **Auth'un token üretme ucu:** community, Auth'a servis kimliğiyle (`client_credentials` benzeri) başvurup kısa ömürlü `svc+jwt` alır. Gerekli: uç sözleşmesi, community'nin Auth'a kendini nasıl tanıtacağı (paylaşılan sır mı, ağ izolasyonu mu), izinli `aud` ve `scope` listesi.
2. **Ortak servis-token kütüphanesi:** Auth'un `ServiceTokenIssuer`'ı gibi bir bileşen ortak modül olarak yayınlanır. Bu durumda her servis kendi özel anahtarını tutar; ama **tek aktif anahtar/JWKS** modeliyle çelişir, bu yüzden bu seçeneği önermiyoruz.

Hangi seçilirse seçilsin community şunu garanti eder: token'ı loglamaz, her çağrıda yenisini kullanır (ya da süresi dolmadan önbelleğe alır), 4xx/5xx'te mevcut outbox yeniden deneme/geri çekilme mantığıyla tekrar dener.

## 3. Neden gerekli

- Moderasyon kararı ve engelleme etkisi, community'de içeriğin görünürlüğünü değiştirir. Bu uçlar **sahte olayla** kötüye kullanılırsa herhangi bir içerik gizlenebilir veya bir kullanıcının engeli yok sayılabilir. Çağıranın kimliği, zarftaki `producer` alanı gibi kendi beyanıyla **kanıtlanamaz**; Auth'un imzaladığı token şarttır.
- Giden tarafta token olmadan alıcı servisler (admin, notification) çağrıyı doğru şekilde reddeder; olaylar outbox'ta `PENDING` bekler. Veri kaybı yoktur ama bildirim/rapor akışı çalışmaz.

## 4. Kabul kriteri

- Auth `docs-auth/servis-sozlesmeleri.md`'ye çağıran/kapsam tablosunu (hangi servis hangi hedefe hangi `scope` ile çağırabilir) ekler.
- Community, gerçek bir `ServiceTokenIssuer` çıktısıyla `POST /internal/events` çağrısını `200` ile, yanlış `sub`/`scope`/`aud` ile `401/403` ile yanıtlar (uyumluluk testi Auth sahibiyle birlikte yapılır).
- Relay açılır (`ltz.outbox.relay.enabled=true`) ve giden çağrılar token'la teslim edilir.

## 5. Beklerken community'nin davranışı (hiçbir şey kırılmaz)

- `/internal/events` ucu **kapalı** (kapsamlı token yoksa 403/401); moderasyon ve engelleme olayları community'ye ulaşamaz. Bu yüzden engelleme kuralı (COM-21) ve moderasyon uygulaması (COM-17) kod ve testlerle hazır olsa da **canlıda tetiklenemez**.
- Outbox olayları `PENDING` bekler (relay kapalı); veri kaybolmaz, hesap silinirse hesabın kimliğini taşıyanlar silinir.
- Kapsam adı tek yerde sabit: `TokenClaims.EVENT_INGEST_SCOPE` (`events:ingest`). Auth farklı ad seçerse yalnız bu sabit ve `SecurityConfig` etkilenir.

> **Cevap (Kerem, 2026-10-28):** Genişletmeye açık, varsayılan imtiyaz yok; her çağıran için ayrı tanım (kim → hangi servis → hangi kapsam). `events:ingest` adı `kaynak:eylem` kuralına uygun. Giden çağrılar için token, Auth'un token ucundan; community için ayrı istemci kimliği + gizli anahtar (ortam değişkeninden). İstenen kapsam listesi `auth-requests-04.md` §2'dedir.

## 6. Kerem'e iletilecek sorular

1. D4 servis token'ı `account:erase` dışında kapsamlara ve `svc:auth-service` dışındaki çağıranlara genişletilecek mi? Evetse zamanlama?
2. Giden çağrılar için (community → admin/notification) token'ı nasıl edinmeliyiz (§2.2 seçenek 1 mi, başka mı)?
3. Kapsam adı olarak `events:ingest` uygun mu, yoksa Auth'un bir adlandırma kuralı var mı (örn. `<kaynak>:<eylem>`)?

## 7. Etkilenen community kodu (bilgi için)

`infrastructure/security/SecurityConfig` (yol → kapsam kuralları), `TokenClaims` (`EVENT_INGEST_SCOPE`), `api/controller/InternalEventController` (`sub` = `svc:<producer>`), `infrastructure/client/EventDeliveryClient` (giden çağrı; token eklenecek yer), `infrastructure/messaging/outbox/OutboxRelay` (varsayılan kapalı).
