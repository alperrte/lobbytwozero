# Social Service — Servisler Arası Entegrasyon (TASK-06)

> Tarih: 2026-11-09 · Sorumlu: Umut · Servis: `social-service` · Kapsam: SOC-12 gizlilik adaptörü, SOC-13 olay teslimi (user-service), Auth/Community/Messaging bağlantılarının gerçek servislerle doğrulanması · **Kod değişikliği yalnız social-service'te; diğer servislerin koduna dokunulmadı.**
>
> Görev: Social Service'in bağımlı olduğu servislerle entegrasyonun tamamlanması; API iletişimi, veri tutarlılığı ve entegrasyon testleri.

## 1. Sonuç

| # | Bağlantı | Önce | Şimdi | Kanıt |
| --- | --- | --- | --- | --- |
| 1 | Auth → Social: hesap verisi silme (`DELETE /internal/v1/accounts/{id}`) | İki taraf hazır, canlı NOT VERIFIED | **PASS** (canlı) | §5.2 |
| 2 | Social → Community: `UserBlocked` / `UserUnblocked` | İki taraf hazır, relay kapalı, canlı NOT VERIFIED | **PASS** (canlı) | §5.1 |
| 3 | Social → User: gizlilik kararı (SOC-12) | Adaptör yok, başkasının listesi her zaman gizli | **Kod + PASS** (canlı) | §3.1, §5.1 |
| 4 | Social → User: `FriendshipChanged`, `UserBlocked`, `UserUnblocked` | Teslim yok (user hedefi tanımsız) | **Kod + PASS** (canlı) | §3.2, §5.1 |
| 5 | Messaging → Social: engel sorgusu (`blocks/check`) | İki taraf hazır, canlı NOT VERIFIED | **PASS** (canlı) | §5.1 |
| 6 | LFP → Social: engel sorgusu | lfp-service iskelet | Değişmedi (LFP işi) | §6 |
| 7 | Social → Notification: arkadaşlık bildirimleri (SOC-13) | notification-service iskelet | Değişmedi, olaylar outbox'ta bekler | §6 |

## 2. Sözleşmeler (karşı tarafın kodundan doğrulandı)

| Uç | Gövde | Yetki |
| --- | --- | --- |
| user `POST /internal/v1/privacy/decisions` | İstek `{ field: "FRIENDS_LIST", ownerIds: [uuid] }` (`viewerId` isteğe bağlı); yanıt `{ decisions: [{ ownerId, setting: PUBLIC\|FRIENDS\|PRIVATE, allowed, friends }] }` | `aud=user-service`, `scope=privacy:read`, `sub ∈ {svc:social-service, …}` |
| user `POST /internal/events` | Zarf (architecture §6.2). `FriendshipChanged {userLowId, userHighId, change: ADDED\|REMOVED}`, `UserBlocked/UserUnblocked {blockerId, blockedId}` — Social'ın ürettiği yükle **birebir aynı alanlar** | `aud=user-service`, `scope=events:ingest`, `sub=svc:social-service` (Auth dışı üreticide hesaba bağlı token gerekmez) |
| community `POST /internal/events` | Aynı zarf; `UserBlocked/UserUnblocked` | `aud=community-service`, `scope=events:ingest` |
| Auth izinleri | `social-service → community-service: events:ingest`, `social-service → user-service: privacy:read,events:ingest` (auth `application.properties`) | — |

İki alıcı da `eventId` ile tekilleştirir (user `EventOutcome.DUPLICATE`, community inbox). Bu, bir olayın birden fazla hedefe "en az bir kez" gönderilmesini güvenli kılar.

## 3. Değişiklikler (social-service)

### 3.1 Gizlilik adaptörü (SOC-12)

- `UserPrivacyClient` (`FriendsVisibilityPort`): user-service gizlilik kararı ucundan **yalnız sahibin `setting` değerini** okur, `viewerId` göndermez. Arkadaşlık ve engel kararı yine Social'ın kendi verisiyle `FriendsListAccessPolicy`'de verilir; user-service'in yansıması olay gecikmesiyle geride kalabileceği için onun `allowed` sonucuna güvenilmez.
- Fail-closed: servis kimliği yok, token alınamadı, hata yanıtı, karar eksik ya da tanımsız ayar → `PrivacyUnavailableException` → `PRIVATE`. İletilere token/gövde yazılmaz.
- `ltz.social.privacy.adapter=${SOCIAL_PRIVACY_ADAPTER:user}` (varsayılan `user`; `unavailable` seçilebilir).

### 3.2 Olay teslimi: tek hedeften çok hedefe

- `ServiceEventPublisher`: `CommunityEventPublisher`'daki HTTP gönderimi (token, zarf, 2xx dışı = teslim yok, gizli bilgi yazmama) ortak sınıfa çıkarıldı; davranış aynı.
- `CommunityEventPublisher` (`UserBlocked`, `UserUnblocked`) ve yeni `UserEventPublisher` (`FriendshipChanged`, `UserBlocked`, `UserUnblocked`) bundan türer.
- `FanOutEventPublisher`: olayı türünü işleyen her hedefe sırayla iletir. Biri alamazsa olay teslim edilmemiş sayılır, relay tamamını yeniden dener (alıcılar tekilleştirir). Hiçbir hedefin işlemediği tür `No delivery target` ile outbox'ta kalır.
- `ltz.social.outbox.adapter=${SOCIAL_OUTBOX_ADAPTER:services}` (yeni varsayılan; `community` ve `unavailable` korunur). Relay varsayılanı **değişmedi** (`SOCIAL_OUTBOX_RELAY_ENABLED=false`).
- `AuthServiceTokenClient` artık koşulsuz bean (gizlilik adaptörü de kullanır); gizli anahtar yoksa hiçbir giden çağrı denenmez.
- `ServiceClientProperties`: `userServiceUrl` (`${USER_SERVICE_URL:http://user-service:8080}`; compose ortak ortamında zaten var).
- `docker-compose.yml`: yalnız social yorumları güncellendi.

## 4. Otomatik testler

| Test | Kapsam |
| --- | --- |
| `UserPrivacyClientTest` (11) | Doğru uç/token/gövde (`viewerId` yok), üç ayarın eşlenmesi, anahtar yok/token yok/5xx/403/eksik karar/boş yanıt/bilinmeyen ayar → fail-closed, iletide token ve gövde yok |
| `UserEventPublisherTest` (4) | `FriendshipChanged` ve engel olayları user'a token'la, ilgisiz türler hiç çağrı yapmadan reddedilir, hata iletisinde gövde yok |
| `FanOutEventPublisherTest` (5) | Engel olayı community + user, arkadaşlık yalnız user, hedefsiz tür, bir hedef düşerse teslim yok, hazırlık |
| `CommunityEventPublisherTest`, `AuthServiceTokenClientTest` | Değişmeden geçiyor (yalnız yeni özellik parametresi eklendi) |

`mvn -pl services/social-service -am clean verify`: **238 test PASS** (önce 218). Yeni Checkstyle/PMD ihlali **0** (`lint-ratchet check`: 137 → 137).

## 5. Canlı entegrasyon (gerçek yığın, 2026-11-09)

Yığın: `node ops/dev.mjs --all --force --no-build` (20 servis), `.env.dev`'de `SOCIAL_OUTBOX_RELAY_ENABLED=true`. Gateway üzerinden gerçek oturumlar (`user`=A, `creator`=B, `developer`=C, `moderator`=M); doğrulama hem API yanıtı hem ilgili servisin veritabanı.

### 5.1 Gizlilik, olaylar, engel sorgusu — 23/23 PASS

| Senaryo | Sonuç |
| --- | --- |
| A `friendsList=PUBLIC` → B, A'nın listesini görür | 200 |
| `PRIVATE` → B göremez | 403 `FRIENDS_LIST_NOT_VISIBLE` |
| `FRIENDS`, arkadaş değil → göremez; davet koduyla arkadaş olunca → görür; arkadaşlık bitince → yine göremez | 403 / 200 / 403 |
| Arkadaşlık kuruldu / bitti → `user_db.social_friendships` | `active=t` / `f` (~11 sn, relay 10 sn'de bir) |
| A, C'yi engeller → `community_db.user_blocks` ve `user_db.social_blocks` | satır var / `active=t` |
| Engel varken C→A ve A→C birebir sohbet (messaging → social `blocks/check`) | 403 `MESSAGING_NOT_ALLOWED` (iki yön) |
| Engel varken C, A'nın listesini görür mü | 403 |
| Engel kalkınca → community satırı silinir, user `active=f`, sohbet açılır | PASS, 201 |
| Outbox | `FriendshipChanged` ×2, `UserBlocked`, `UserUnblocked` **SENT**, hepsi ilk denemede; `FriendRequestSent/Accepted` PENDING (hedef yok, beklenen) |

### 5.2 Auth → Social hesap silme — PASS

Geçici ayar: mail Mailpit'e (`dev.mjs mail off`), `AUTH_DELETION_GRACE_PERIOD=PT1M`. M'ye arkadaşlık, engel ve davet kodu oluşturuldu (Social'da 4 satır) → silme isteği → Mailpit'teki token ile onay → **82 sn** sonra Auth'taki `social` görevi `CONFIRMED`, M'ye ait Social satırı **0**, M A'nın listesinden düştü. user_db ve community_db'de de M'ye ait yansıma kalmadı (her servis kendi silme ucunda temizliyor; Social'ın ayrıca olay üretmesine gerek yok). **Test sonrası iki ayar geri alındı** (bekleme süresi P14D, Gmail SMTP açık). `moderator@ltz.com` bu testte silindi; sonraki `dev.mjs` çalıştırmasında yeniden oluşur.

## 6. Sorumlulara iletilecekler

| Kime | Konu |
| --- | --- |
| Kerem (Auth, User) | Social → user `privacy:read` ve `events:ingest` çiftleri canlı doğrulandı; Auth → Social silme `CONFIRMED`. user-service `ltz.user.social.projection-enabled=false` (sabit, ENV yok): Social artık `FriendshipChanged/UserBlocked/UserUnblocked` teslim ediyor; yansımanın gizlilik kararlarında kullanılması için bu bayrağın açılması user-service kararı. `docs/api.md` user gizlilik kararı ucundaki "Gerçek tüketicilerle uçtan uca: NOT VERIFIED" social için güncellenebilir. |
| Community sorumlusu | Social → community `UserBlocked/UserUnblocked` canlı teslim edildi ve `user_blocks` doğru güncellendi. Relay varsayılanının (`SOCIAL_OUTBOX_RELAY_ENABLED`) `true` yapılması için onay. |
| LFP sorumlusu | lfp-service iskelet: Social `POST /internal/v1/blocks/check` hazır, Auth çifti (`lfp-service → social-service: blocks:check`) tanımlı. Ayrıca Auth hesap silme görevi `lfp` için 401 (silme ucu yok). |
| Notification sorumlusu | SOC-13: `FriendRequestSent`, `FriendRequestAccepted` olay sözleşmesi `docs/api.md` → "Giden olay sözleşmeleri"; Auth'ta `social-service → notification-service` çifti ve alıcı uç gerekli. Ayrıca silme görevi 401. |
| Messaging (Umut) | Engel sorgusu canlı PASS. **Hesap silme ucu (`DELETE /internal/v1/accounts/{id}`) yok**: Auth görevi `messaging` 401 ile PENDING kalıyor. Ayrı task. |
| Game sorumlusu | Auth silme görevi `game` **HTTP 500** dönüyor (diğer eksik servisler 401/403); game-service silme ucunda hata var. |
| admin, content, event, recommendation | Auth silme görevleri 401/403 ile PENDING (silme ucu yok); hesap hiçbir zaman `DELETED` olamaz. |

## 7. Açık noktalar

| # | Konu | Not |
| --- | --- | --- |
| 1 | Relay varsayılanı | Hâlâ `false`; açılması ekip kararı (§6 Community). Kapalıyken olaylar outbox'ta birikir, açılınca sırayla teslim edilir. |
| 2 | Bildirim olayları | Hedef yokken relay açıksa `FriendRequestSent/Accepted` 10 denemeden sonra `FAILED` olur (mevcut davranış, değişmedi). notification hedefi gelince `FanOutEventPublisher`'a eklenir. |
| 3 | Kullanıcı varlık doğrulaması | İstek/engel hedefinin user-service'te var olup olmadığı hâlâ doğrulanmıyor (TASK-01/03'ten açık). |
| 4 | Canlı test betikleri | Repoya eklenmedi; senaryolar §5'te. Tekrarlanabilir bir ops betiği istenirse `ops/` kuralına (test dosyasıyla) eklenmeli. |
| 5 | Node sürümü | `ops/dev.mjs` ve güncel ops testleri Node 24.7+ istiyor; bu makinede Node 22.20 var (`npx node@24` ile çalıştırıldı). |
