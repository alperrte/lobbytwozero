# LTZ-UMT-SOC-02 — Arkadaş Listesi ve Davet Kodu (TASK-02)

> Tarih: 2026-10-22 · Sorumlu: Umut · Servis: `social-service` · Kapsam: SOC-07, SOC-08, SOC-13, SOC-14 · Durum: **kod ve testler tamam; SOC-13'ün teslim (relay) tarafı bilerek PENDING; tam kapı (`full dev`) ve Sonar, social dışı bir engelle doğrulanamadı**
>
> Bu belge social-service çalışmasına özgüdür; ortak kurallar `AGENTS.md`, `docs/SECURITY.md` ve `docs/api.md` içindedir. Endpoint ve olay sözleşmeleri `docs/api.md` → `## SOCIAL` altındadır.

## 1. Sonuç

| Görev | Durum | Not |
| --- | --- | --- |
| SOC-07 Arkadaş listesi | IMPLEMENTED | Yalnız kendi arkadaşları; keyset sayfalama; yalnız kimlik (profil user-service'te) |
| SOC-08 Arkadaşlıktan çıkarma | IMPLEMENTED | Atomik silme; iki taraf da çıkarabilir; başkasının arkadaşlığı etkilenmez |
| SOC-13 Arkadaşlık bildirimleri | **Kısmen** | Olay sözleşmesi + transactional outbox + relay **tamam ve testli**; tüketiciye gerçek teslim **PENDING** (aşağıda) |
| SOC-14 Davet kodu | IMPLEMENTED | Üret / getir / iptal et + kodla arkadaşlık isteği başlat |

Doğrulama durumu:

| Kontrol | Sonuç |
| --- | --- |
| Birim + entegrasyon testleri (Testcontainers, gerçek PostgreSQL 17) | PASS — **126 test** |
| `.\pre-push\pre-push.cmd social-service` | PASS — `BACKEND TEST CHECK PASSED`, satır kapsamı **%95,45** |
| `.\pre-push\pre-push.cmd full dev` | **NOT SAFE TO COMMIT** — Git/Secret, Docker Configuration ve Backend HTTP Health (social V122/V123 ile yeniden build edilip ayağa kalktı) PASS; Backend Tests/Build/Coverage FAIL → **yalnız `game-service` migration testleri** (aşağıya bakın). social-service ve diğer 16 modül PASS |
| `.\sonarqube\sonar.cmd scan dev` | **NOT VERIFIED** — Sonar tüm modüllerin başarılı test raporlarını şart koştuğundan aynı engelle koşulmadı |

### Tam kapıyı engelleyen social dışı hata

`game-service` (Hamza) içinde `MigrationArchitectureTest` (2 hata), `MigrationTransferTest` (3 hata, 2 error) ve `MigrationTransferApplicationTest` (1 error) `SCHEMA_MISMATCH` ile düşüyor. Tek başına tekrar koşulunca da aynı sonuç (kalıcı, kararsız değil). `game-service` dizininde yerel değişiklik yok; hata main'in (Hamza'nın PR'ı) mevcut hâlinde. Social kodundan bağımsızdır; sahibine bildirilmeli. Bu çözülmeden `full dev` / Sonar bu makinede yeşil olmaz.

## 2. Akış ve kurallar

**Arkadaş listesi (SOC-07)** `GET /api/v1/social/friends?limit&cursor` — en yeni arkadaşlıktan eskiye. Arkadaş, tek satırlık çiftin kullanıcı olmayan tarafıdır; sıralama `(created_at, friendId)` ikilisiyle kararlıdır.

**Çıkarma (SOC-08)** `DELETE /api/v1/social/friends/{friendId}` → `204`. İşlem token sahibi ile `friendId` çiftine bağlı olduğundan IDOR yoktur; arkadaş değilse (ya da kendisiyse) `404`. Silme tek `DELETE` ile yapılır, eşzamanlı iki çıkarmadan yalnız biri `204` alır. İstek geçmişi korunur; çıkarmadan sonra yeniden istek gönderilebilir.

**Davet kodu (SOC-14)**
- `POST /invite-code` idempotenttir: etkin ve kullanılabilir kod varsa `200` ile onu, yoksa (hiç yok / süresi dolmuş / kullanım hakkı bitmiş) eskisini iptal edip yenisini üretir (`201`). `GET` kendi kodunu, `DELETE` iptalini yapar.
- Kod `SecureRandom` ile üretilen 10 karakterlik Crockford base32'dir (50 bit; I, L, O, U yok). Varsayılan 30 gün geçerli, en fazla 50 kullanım.
- `POST /friend-requests/by-code {code}` → kod sahibine **normal arkadaşlık isteği**. Aynı `FriendRequestService.send` kullanıldığından kendine istek, zaten arkadaş, bekleyen istek ve istek sınırı kuralları aynen geçerlidir. Kullanım sayacı yalnız istek gerçekten oluştuğunda artar (aynı transaction; ret → geri alınır).
- Bilinmeyen / biçimsiz / iptal edilmiş / süresi dolmuş / kullanım hakkı bitmiş kod **aynı `404`** döner (kodun varlığı sızdırılmaz). Elle yazılan kod büyük/küçük harf, boşluk ve tireden bağımsız eşleşir (I,L→1; O→0).
- Eşzamanlılık: kullanıcı başına tek etkin kod DB'de kısmi tekil indeksle; kullanım sınırı kod satırı kilidiyle (`max_uses=1` iken 6 eşzamanlı kullanımdan yalnız biri başarılı).

**Olaylar (SOC-13)** Olay, iş verisiyle **aynı transaction'da** `outbox_events` tablosuna yazılır (`Propagation.MANDATORY`: transaction dışında yazılamaz; iş geri alınırsa olay da geri alınır).

| Olay | Ne zaman | Tüketici |
| --- | --- | --- |
| `FriendRequestSent` v1 | İstek oluştu (normal ya da kodla) | notification |
| `FriendRequestAccepted` v1 | İstek kabul edildi | notification |
| `FriendshipChanged` v1 (`ADDED`/`REMOVED`) | Arkadaşlık kuruldu / bitti | messaging, recommendation |

Red ve iptal olay üretmez. Yükler yalnız kimlik/enum taşır.

## 3. SOC-13 neden "kısmen"

PDA maddesi "olayların notification-service ile **sözleşme bazlı** paylaşılması"dır. Sözleşme (olay türleri, sürüm, yük, zarf) yazıldı ve outbox'ta üretiliyor. Gerçek teslim şunlara bağlı ve ikisi de bu repoda yok:

1. `notification-service` bir iç uç sunmuyor (servis iskelet; ortak/sahipsiz — AGENTS.md gereği değiştirilmedi).
2. Servisler arası kimlik (D4, `typ=svc+jwt`) yok; kimliksiz bir iç uca olay göndermek SECURITY.md'ye aykırıdır, bu yüzden **uydurulmadı**.

Bu yüzden relay varsayılan **kapalı** (`ltz.social.outbox.relay-enabled=false`) ve teslim adaptörü `unavailable`; olaylar `outbox_events`'te `PENDING` bekler (kaybolmaz). Relay mantığının kendisi (en az bir kez teslim, `SKIP LOCKED` + kira ile çift teslim önleme, üstel geri çekilme, 10 denemeden sonra `FAILED`, bir olayın hatası diğerlerini engellemez) sahte yayıncıyla **testlidir**. Gerçek adaptör için yapılacak: `SocialEventPublisher` uygulaması + `relay-enabled=true`.

## 4. Güvenlik

| Katman | Koruma |
| --- | --- |
| Kimlik / yetki | SOC-01 ile aynı: JWKS ile doğrulanan Bearer, deny-by-default; `userId` yalnız token `sub` |
| IDOR | Arkadaş listesi ve çıkarma token sahibine bağlı; kod uçları yalnız kendi kodunu görür/iptal eder |
| Kod gizliliği | 50 bit rastgelelik; tüm geçersiz durumlar aynı `404`; kod loglanmaz |
| Girdi | `code` ≤ 32 karakter, `limit`/`cursor` sunucuda doğrulanır, bilinmeyen alan reddedilir |
| Eşzamanlılık | Kısmi tekil indeksler, satır kilitleri, atomik silme, `@Version` |
| Outbox | Yük kişisel veri içermez; loglara yalnız olay türü ve kimliği yazılır |

Not: kodla istek denemelerine ayrı bir oran sınırı yok (50 bit rastgelelik + bekleyen istek sınırı); IP/kullanıcı bazlı oran sınırı Gateway'de social için **NOT IMPLEMENTED** (SOC-01 açık noktası 6 ile aynı).

## 5. Veritabanı

`V122__create_outbox_events.sql` (outbox), `V123__create_friend_invite_codes.sql` (davet kodu). Ayrıntı: `docs/database.md` §3.7, `docs/er-diagram.md` §8 (aynı değişiklikte güncellendi). Sıradaki migration numarası: **V124** (social aralığı V121–V130).

`docs/architecture.md` §6.3 olay kataloğuna `FriendRequestSent` ve `FriendRequestAccepted` eklendi.

## 6. Gereksinim → test → dosya kanıtı

| Madde | Test | Ana dosya |
| --- | --- | --- |
| SOC-07 liste, yalnız kendi arkadaşları, sıralama, sayfalama, sınırlar | `FriendshipControllerTest` | `FriendshipService.listFriends`, `FriendshipRepository` |
| SOC-08 çıkarma, iki taraf, IDOR, tekrar çıkarma `404`, eşzamanlı çıkarma | `FriendshipControllerTest` | `FriendshipService.unfriend`, `FriendshipRepository.deleteByFriendshipId` |
| SOC-14 kod üretimi idempotent, tek etkin kod, süre/iptal/bitiş sonrası yenileme, eşzamanlı üretim | `InviteCodeControllerTest` | `InviteCodeService.getOrCreate`, `V123` |
| SOC-14 kodla istek, normalleştirme, kendi kodu, tüm geçersiz durumlar aynı `404` | `InviteCodeControllerTest` | `InviteCodeService.redeem`, `InviteCodeGenerator` |
| SOC-14 normal kurallar (zaten arkadaş, bekleyen istek) ve sayaç geri alma | `InviteCodeControllerTest` | `FriendRequestService.send` |
| SOC-14 kullanım sınırı, eşzamanlı kullanımda sınır aşılmaz | `InviteCodeControllerTest` | `InviteCodeRepository.findByCodeForUpdate` |
| Kod üretimi (biçim, benzersizlik, normalleştirme) | `InviteCodeGeneratorTest`, `InviteCodeTest` | `InviteCodeGenerator`, `InviteCode` |
| SOC-13 olayların yazılması, yükün içeriği, red/iptal olay üretmez | `OutboxEventsTest`, `FriendshipControllerTest` | `OutboxEventRecorder`, `domain/event/*` |
| SOC-13 iş geri alınırsa olay da geri alınır; transaction dışında yazılamaz | `OutboxEventsTest` | `Propagation.MANDATORY` |
| SOC-13 relay: teslim, yeniden deneme, `FAILED`, kısmi hata, çift teslim yok | `OutboxRelayTest`, `OutboxEventTest` | `OutboxRelay`, `OutboxEventStore`, `OutboxEvent` |
| Şema kısıtları (V122, V123) | `FlywayMigrationTest` | `V122`, `V123` |
| Kimliksiz erişim `401` | `JwtValidationTest`, `InviteCodeControllerTest` | `SecurityConfig` |

## 7. Açık noktalar

| # | Konu | Durum |
| --- | --- | --- |
| 1 | Olayların gerçek teslimi (SOC-13) | notification-service iç ucu ve servis kimliği (D4) bekleniyor; relay kapalı, olaylar outbox'ta |
| 2 | Engelleme kuralının istek ve kod akışına bağlanması | SOC-09 ile (TASK-03); iki akış aynı `FriendRequestService.send`'i kullandığından tek yerde uygulanacak |
| 3 | Başkasının arkadaş listesi ve gizlilik tercihi | SOC-12 (TASK-03); user-service tercihlerine bağlı |
| 4 | Arkadaş listesinde ad/avatar | user-service'in profil özeti ucu bekleniyor |
| 5 | Outbox saklama/temizlik (`SENT` 7 gün) | Teslim olmadığından henüz `SENT` kayıt yok; relay açılırken zamanlanmış silme eklenmeli |
| 6 | `game-service` migration testleri `SCHEMA_MISMATCH` | **Hamza'ya bildirilmeli**; çözülmeden `full dev` ve Sonar yeşil olmaz |
| 7 | Kodla istek denemelerine oran sınırı, Gateway'de social için oran sınırı | NOT IMPLEMENTED |

## 8. Commit öncesi kontrol listesi

1. `game-service` migration testleri sorununu çözdürün (ya da main'de de düştüğünü teyit edin) — bu makinede `full dev` ve Sonar buna bağlı.
2. `.\pre-push\pre-push.cmd full dev` → `SAFE TO COMMIT`.
3. `.\sonarqube\sonar.cmd scan dev` (Security / Reliability / Maintainability A; kapsam ≥ %80; tekrar ≤ %3).
4. Sonuçları §1 tablosuna işleyin.
