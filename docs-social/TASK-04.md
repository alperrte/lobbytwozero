# Social Service — Auth Entegrasyonu 2. Aşama (TASK-04)

> Tarih: 2026-10-29 · Sorumlu: Umut · Servis: `social-service` · Kaynak: `docs-auth/entegrasyon/social-service.md`, `docs-auth/entegrasyon/README.md`, `docs-auth/verilen-cevaplar/social-service/talep-01-servis-token-sikilastirma.md`, `docs-auth/d4-ekip-notlari/02_UMUT_social_messaging_lfp.md` · **Auth-service, api-gateway ve diğer servislerin kodunda değişiklik yapılmadı.**
>
> Bu belge `TASK-AUTH-ENTEGRASYON.md` (1. aşama) üzerine eklenir: Auth main'e alındıktan sonra Social tarafında kalan dört iş.

## 1. Sonuç

| # | İş | Durum | Kanıt |
| --- | --- | --- | --- |
| 1 | Silme ucu, açık 1: `sub` tam `svc:auth-service` | **KAPANDI** | `AccountErasureController`, `AccountErasureTest` (`baskaServisinTokeniHesapSilemez403VeriSilinmez`) |
| 2 | Silme ucu, açık 2: `account_id` = yoldaki `accountId` | **KAPANDI** | `AccountErasureController`, `tokenBaskaHesabaBagliysa403VeriSilinmez`, `yolVeClaimdeAyniUuidFarkliHarfBuyuklugunde204` |
| 3 | `POST /internal/v1/blocks/check` (messaging, lfp) | **YAZILDI** | `BlocksCheckController`, `BlockCheckService`, `BlocksCheckTest` (13 test) |
| 4 | Gerçek olay yayıncısı (`UnavailableEventPublisher` yerine) | **YAZILDI, relay kapalı** | `CommunityEventPublisher`, `AuthServiceTokenClient`, iki birim test sınıfı |

## 2. Yapılanlar

**Silme ucu (iş 1-2).** `AccountErasureController` artık `sub == "svc:auth-service"` ve `account_id == accountId.toString()` ister; aksi halde `AccessDeniedException` → `403 FORBIDDEN`, hiçbir şey silinmez. Karşılaştırma yoldan çözülen `UUID`'in `toString()` değeriyle yapılır (büyük harfli yol ile küçük harfli claim eşleşir). `JwtConfig`'teki genel `svc:` önek kuralı aynen kaldı. `IntegrationTestSupport.validServiceClaims(UUID)` artık `account_id` üretir; mevcut silme testleri hesaba bağlı token kullanır.

**Engel sorgusu (iş 3).** Gövde `{userId, otherUserIds[1..100]}`, yanıt `{results:[{userId, blocked}]}`. `blocked`, iki yönden birinde engel varsa `true`. Yetki iki katmanlı:
- `SecurityConfig`: `POST /internal/v1/blocks/check` → `SCOPE_blocks:check`
- `BlocksCheckController`: `sub` yalnız `svc:messaging-service` ya da `svc:lfp-service` (kapsam doğru olsa bile başka servis `403`)

Silme token'ı (`account:erase`) bu uca yetmez; kullanıcı token'ı `401`.

**Olay yayıncısı (iş 4).**
- `AuthServiceTokenClient`: `POST {AUTH_SERVICE_URL}/internal/v1/service-tokens` `{clientId: "social-service", clientSecret, audience}`; hedef başına ayrı token, 60 sn ömrün 15 sn payla kısa önbelleği, gizli anahtar yoksa fail-closed, hata iletilerinde anahtar/gövde yok.
- `CommunityEventPublisher`: `UserBlocked` ve `UserUnblocked` → `POST {COMMUNITY_SERVICE_URL}/internal/events` (`Authorization: Bearer`, `payload` iç içe JSON nesnesi, 2xx dışı yanıt başarısız).
- `SocialEventPublisher.isReady()`: gizli anahtar yoksa `OutboxRelay` olayları hiç almaz, deneme hakkı yanmaz.
- Adaptör varsayılanı `community` (`SOCIAL_OUTBOX_ADAPTER`), relay varsayılanı **kapalı** (`SOCIAL_OUTBOX_RELAY_ENABLED=false`).

**Yapılandırma.** Yeni ENV değişkeni **eklenmedi**: `AUTH_SOCIAL_SERVICE_CLIENT_SECRET` zaten `.env.example`'da var; `docker-compose.yml` social ortamına bu değişken ve `SOCIAL_OUTBOX_RELAY_ENABLED` (varsayılan `false`) eklendi. Gerçek `.env` dosyalarına dokunulmadı. Gizli anahtarı üretmek için: `node ops/generate-auth-dev-keys.mjs`.

**Belgeler.** `docs/api.md` SOCIAL bölümü: engel sorgusu `PLANNED` → `IMPLEMENTED`, silme kaydı yeni yetki kurallarıyla, olay kaydı teslim durumuyla güncellendi.

## 3. Doğrulama

| Kontrol | Sonuç |
| --- | --- |
| `.\pre-push\pre-push.cmd social-service` | **PASS**: `BACKEND TEST CHECK PASSED`, 218 test (önceki 184 + 34 yeni), satır kapsamı %96,14, değişen satırlar %99,4 |
| Checkstyle/PMD yeni ihlal denetimi | **PASS**: yeni ihlal 0 (eski borç 137, değişmedi; yeni test adları alt çizgisiz) |
| Gerçek Auth + community ile uçtan uca | **NOT VERIFIED** (aşağıda) |
| `.\pre-push\pre-push.cmd full dev` / SonarQube | Çalıştırılmadı |

## 4. Açık noktalar

| # | Konu | Durum |
| --- | --- | --- |
| 1 | Gerçek `svc+jwt` ile canlı deneme: Auth'tan `blocks:check` / `account:erase` / `events:ingest` token'ı alıp Social'a vurmak | `AUTH_SOCIAL_SERVICE_CLIENT_SECRET` ile `docker compose up` sonrası yapılacak; testlerde token kendi RSA anahtarımızla imzalandı |
| 2 | Relay'i açmak (`SOCIAL_OUTBOX_RELAY_ENABLED=true`) | Community alıcısı gerçek token'la doğrulanınca. **Dikkat:** açılırsa `FriendRequestSent`, `FriendRequestAccepted`, `FriendshipChanged` olayları hedef olmadığı için 10 denemede `FAILED` olur |
| 3 | Social → notification / messaging / recommendation olay hedefleri | Auth'ta çift tanımlı değil, alıcı uçlar da yok; ihtiyaç `docs-auth/verilen-cevaplar/social-service/talep-NN-*.md` olarak yazılacak |
| 4 | Messaging ve LFP tarafı (JWT, silme ucu, `blocks/check` istemcisi) | İskelet servisler, bu görevin dışında |
| 5 | Kullanıcı varlık doğrulaması ve gizlilik tercihi (`FriendsVisibilityPort`) | user-service yazılınca |
| 6 | `docs-auth/entegrasyon/social-service.md` ve talep-01 durumu | Auth sahibine (Kerem) bildirilecek: silme açıkları ve `blocks/check` ucu Social'da kapandı |
