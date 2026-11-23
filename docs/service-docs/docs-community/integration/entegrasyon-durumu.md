# community-service — servis entegrasyon durumu (2026-11-07)

> Sahip: Nisa. Kapsam: yalnız `community-service`. Bu belge COM-01…COM-21 gereksinimlerini **karşı servis bağımlılığı** açısından tek tek denetler. Auth ayrıntıları `auth/entegrasyon-v2/auth-integration-05.md`, Game ayrıntıları `docs/community/game/game-01-cevap.md`, Admin/Notification talepleri `docs-community/<servis>/talep/` altındadır.
> Doğrulama düzeyi: Tüm karşı servis testleri sahte sunucuyla/yerel testlerle yapıldı. Gerçek Auth/Gateway/Game/Social/Admin/Notification ile **uçtan uca doğrulama yapılmadı (NOT VERIFIED)**.

## 1. Gereksinim → entegrasyon denetimi

| Gereksinim | Karşı servis | Yön | Durum |
| --- | --- | --- | --- |
| COM-01…COM-13, COM-15, COM-19 (forum yerel), COM-20 | — (yalnız Community verisi) | — | Entegrasyon gerekmez; Auth kimliği dışında dış çağrı yok |
| Tüm kullanıcı uçları (kimlik, rol) | **auth-service** | JWKS ile JWT doğrulama | **Tamam** (RS256, `iss/aud/exp/nbf/iat/sub`, `roles`) |
| Giden servis çağrıları için token | **auth-service** | Community → Auth `POST /internal/v1/service-tokens` | **Tamam** (hedefe bağlı, ≤ 60 sn, gizli anahtar boşsa kapalı) |
| Hesap silme (COM-21) | **auth-service** | Auth → Community `DELETE /internal/v1/accounts/{id}` | **Tamam** (V182 ile olay sırası işareti de silinir) |
| `AccountStatusChanged` | **auth-service** | Auth → Community olay | Alıcı **tamam**, içeriğe etki yok (ürün kararı bekliyor); Auth tarafında `AUTH_EVENT_TARGETS=community` eklenmesi "alıcı yayında" haberini bekliyor |
| COM-10 görseller | — | — | Entegrasyon yok (`media-service` kullanılmaz, kurala uygun) |
| COM-14, COM-19 oyun doğrulaması | **game-service** | Community → Game `POST /api/v1/game/games/batch` (anonim) | **Tamam**; Game kesintisi `503`, bilinmeyen oyun `422` |
| GAME-11 oyuna bağlı içerik | **game-service** (tüketici) | Frontend → Gateway → Community | Community sağlayıcısı **tamam**; Game modül DTO'su ve frontend bağlantısı karşı tarafta (Hamza) bekliyor |
| COM-21 engelleme | **social-service** | Social → Community `UserBlocked/UserUnblocked` | Alıcı **tamam**; bu çalışmada olay **sırası** düzeltildi (V182); Social'ın relay'i (`SOCIAL_OUTBOX_RELAY_ENABLED`) kapalı, "alıcı yayında" haberini bekliyor |
| COM-16 raporlama | **admin-service** | Community → Admin `ContentReported` | Üretim ve outbox **tamam**; Admin alıcısı yok → **`docs-community/admin-service/talep/req-01.md`** |
| COM-17 moderasyon | **admin-service** | Admin → Community `ModerationActionTaken` | Alıcı **tamam**; Admin göndericisi yok → **`req-01.md`**; moderatörün içeriği görmesi → **`req-02.md`** (karar bekliyor) |
| COM-18 bildirimler | **notification-service** | Community → Notification `CommentCreated/PostLiked/PostReposted` | Üretim ve outbox **tamam**; Notification alıcısı yok → **`docs-community/notification-service/talep/req-01.md`** |
| Yazar adı/avatarı (gösterim) | user-service | Frontend → Gateway → User `POST /api/v1/user/profiles/batch` | Community yanıtları yalnız `authorId` taşır; çözümleme frontend'dedir. Community'nin User'a çağrısı **gerekmez**, talep açılmadı |
| Yazar hesap durumuna göre gizleme | user-service / auth-service | — | Ürün kararı yok; verilirse `AccountStatusChanged` alıcısına eklenir (yeni sözleşme gerekmez) |

## 2. Açık bırakılanlar (bu çalışmada yapılmadı)

- **Outbox relay kapalı** (`ltz.outbox.relay.enabled=false`). Admin ve Notification alıcıları yayına girmeden açılırsa bekleyen olaylar 10 denemede `FAILED` olur. İki hedef de hazır olunca açılmalı; tek hedefi açan ayar yoktur (eklenmedi, gereksiz bulundu).
- **Moderasyon olaylarında sıra** Community'de korunmaz (yalnız engelleme olaylarında vardır); karar Admin ile `req-01.md` §3.2 madde 3'te bağlanmıştır.
- **Engelleme uzlaştırması:** Social'ın engel sorgu ucu (`POST /internal/v1/blocks/check`) yalnız messaging ve lfp için açıktır. Social olayı 10 denemede teslim edemezse Community yansıması sapabilir; Community'nin çağırması için Auth çifti ve Social izin listesi değişikliği gerekir. Gerçek bir sapma gözlenmediği için talep açılmadı.
- **Game kimlik düzeltme protokolü** (`docs/decisions/0002-oyun-dis-kimlik-duzeltme.md`) taslaktır; Community katılımcı olarak sayılıyor ama sözleşme (prepare/commit) yoktur. Sözleşme gelince Community'ye ait talep ayrıca ele alınır.
- Dev/prod veritabanına V182 uygulanmadı (yalnız izole PostgreSQL testleri).
