# LTZ-UMT-SOC-03 — Kullanıcı Engelleme ve Sosyal Gizlilik (TASK-03)

> Tarih: 2026-10-22 · Sorumlu: Umut · Servis: `social-service` · Kapsam: SOC-09, SOC-10, SOC-11, SOC-12, SOC-15 · Durum: **kod ve testler tamam; SOC-12'nin gerçek gizlilik adaptörü ve SOC-15'in servisler arası engel sorgusu bilerek PENDING**
>
> Bu belge social-service çalışmasına özgüdür; ortak kurallar `AGENTS.md`, `docs/SECURITY.md` ve `docs/api.md` içindedir. Endpoint ve olay sözleşmeleri `docs/api.md` → `## SOCIAL` altındadır.

## 1. Sonuç

| Görev | Durum | Not |
| --- | --- | --- |
| SOC-09 Kullanıcı engelleme | IMPLEMENTED | Aynı işlemde bekleyen istekleri kapatır, arkadaşlığı bitirir, olay yazar; iki yönde kısıtlar |
| SOC-10 Engel kaldırma | IMPLEMENTED | Yalnız kendi koyduğu engeli; karşı tarafın engeli sürer |
| SOC-11 Engellenen kullanıcılar | IMPLEMENTED | Yalnız kendi engelleri; keyset sayfalama |
| SOC-12 Sosyal gizlilik | **Kurallar tamam, adaptör PENDING** | Görünürlük kuralları sunucuda uygulanıyor ve testli; user-service tercih ucu olmadığından başkasının listesi şu an fail-closed gizli |
| SOC-15 Servis sınırları ve entegrasyon | **Kısmen** | Sınırlar test ile doğrulanıyor; auth (JWT), notification (olaylar), user (gizlilik portu) tarafları hazır; messaging/lfp için engel sorgusu ucu sözleşme olarak yazıldı, **uç yazılmadı** (D4 yok) |

Doğrulama durumu: bkz. §8 (kapı sonuçları oraya işlenir).

## 2. Akış ve kurallar

**Engelleme (SOC-09)** `POST /api/v1/social/blocks {blockedUserId}` → `201` (yeni) / `200` (zaten engelli, idempotent). Tek transaction'da:
1. Çift kilidi alınır (`UserPairLock`, PostgreSQL advisory kilidi) — aynı anda gelen istek/kabul ile sıralama.
2. Engel kaydı yazılır.
3. Aralarındaki bekleyen istekler kapanır: engelleyenin gönderdiği `CANCELLED`, engellenenden gelen `REJECTED`.
4. Arkadaşlık varsa silinir ve `FriendshipChanged REMOVED` olayı yazılır.
5. `UserBlocked` olayı yazılır. Engellenene bildirim gitmez.

**Engelin sonuçları**
- Engelleyen o kişiye istek gönderemez: `409 USER_BLOCKED` (önce engeli kaldırmalı).
- Engellenen engelleyene istek gönderemez: `403 FRIEND_REQUEST_NOT_ALLOWED` — **engelin varlığı sızdırılmaz** (genel "bu kullanıcıdan istek alınamaz" hatası).
- Davet kodu akışı aynı `FriendRequestService.send` içinden geçtiği için aynı kurallara tabidir; engel varsa kod sayacı artmaz.
- Engellenen, engelleyenin arkadaş listesini göremez; engelleyen de engellediğinin listesini görmez.

**Engel kaldırma (SOC-10)** `DELETE /blocks/{blockedUserId}` → `204`; engel yoksa `404 BLOCK_NOT_FOUND`. Yön önemlidir: engellenen kişi kendisini engelleyenin engelini kaldıramaz. Karşılıklı engelde biri kalkınca diğeri sürer. Biten arkadaşlık ve kapanan istekler geri gelmez.

**Engellenenler (SOC-11)** `GET /blocks?limit&cursor` — yalnız kendi engelleri, en yeniden eskiye.

**Arkadaş listesi görünürlüğü (SOC-12)** `GET /users/{userId}/friends`:
- Kendi listesi her zaman görünür.
- Aralarında (herhangi bir yönde) engel varsa görünmez.
- Aksi halde user-service tercihi: `PUBLIC` herkes, `FRIENDS` yalnız arkadaşlar, `PRIVATE` kimse.
- Tercih okunamazsa fail-closed (`PRIVATE`).
- Reddedilen her durumda **aynı yanıt** (`403 FRIENDS_LIST_NOT_VISIBLE`): engel, gizlilik tercihi ve olmayan kullanıcı ayırt edilemez.

## 3. Kararlar ve varsayımlar (itiraz edilebilir)

| Konu | Karar | Neden |
| --- | --- | --- |
| `social_privacy_settings` tablosu | **Oluşturulmadı** | PDA: görünürlük tercihi user-service'te yönetilir. `database.md`'deki taslak tablo ile çelişiyordu; Social bu tercihi saklamaz. `who_can_send_requests` gereksinimlerde yok |
| Engellenen kişiye hata | `403` genel hata (engelleyen için `409 USER_BLOCKED`) | Engellenen, engel durumunu öğrenmemeli; engelleyen kendi engelini bilir |
| Engel yönü | Yönlü kayıt, iki yönde kısıtlama | Karşılıklı engelde her taraf kendi engelini yönetir |
| Engel koyunca bekleyen istek | Engelleyenin gönderdiği `CANCELLED`, engellenenden gelen `REJECTED` | Mevcut durum makinesiyle uyumlu; bildirim üretmez |
| Engel koyunca arkadaşlık | Biter | "Kısıtlanan arkadaşlık … işlemlerinin durdurulması" |
| Engel kaldırınca | Eski arkadaşlık/istek geri gelmez | Kullanıcı bilinçli olarak yeniden istek göndermeli |
| Gizlilik adaptörü yokken | Başkasının listesi görünmez (fail-closed) | Gizlilik kuralını gevşetmemek; kimliksiz iç çağrı yapılmaz |
| Engel sorgusu iç ucu | Sözleşme yazıldı, uç yazılmadı | D4 servis kimliği yok; SECURITY.md §19 onay şartı |

## 4. Güvenlik

| Katman | Koruma |
| --- | --- |
| Kimlik / yetki | Önceki task'larla aynı: JWKS ile doğrulanan Bearer, deny-by-default, `userId` yalnız token `sub` |
| IDOR | Engel uçları yalnız token sahibinin engellerini görür/değiştirir; başkasının engelini kaldırmak `404` |
| Bilgi sızıntısı | Engellenen engeli öğrenmez; liste reddinde engel/tercih/olmayan kullanıcı aynı yanıt |
| Eşzamanlılık | Çift kilidi (advisory) + satır kilitleri: engel ↔ istek ve engel ↔ kabul yarışlarında engel sonrası bekleyen istek/arkadaşlık kalamaz; aynı engelin eşzamanlı konması tek kayıt/tek olay |
| Girdi | `blockedUserId` doğrulanır, gövdede bilinmeyen alan (`blockerId`) reddedilir |
| Olay | `UserBlocked` / `UserUnblocked` yükü yalnız iki kimlik taşır |

Yarış testlerinin gerçekten bir şey yakaladığı mutasyonla doğrulandı: `send` içindeki çift kilidi geçici olarak kaldırılınca `BlockConcurrencyTest.engelleIstekGondermeYarisi_...` düştü; kilit geri konup testler tekrar geçti.

## 5. Veritabanı

`V124__create_user_blocks.sql` → `user_blocks` (PK `(blocker_id, blocked_id)`, CHECK farklı kullanıcı, iki indeks). FK yoktur (REF). Ayrıntı: `docs/database.md` §3.7, `docs/er-diagram.md` §8 (aynı değişiklikte güncellendi). Sıradaki migration numarası: **V125** (social aralığı V121–V130; V125–V130 boş).

## 6. Gereksinim → test → dosya kanıtı

| Madde | Test | Ana dosya |
| --- | --- | --- |
| SOC-09 engelleme, idempotent, kendini engelleme, sahte kimlik | `BlockControllerTest` | `BlockService.block`, `BlockController` |
| SOC-09 arkadaşlık biter, bekleyen istekler kapanır (iki durum), ilgisizlere dokunmaz | `BlockControllerTest` | `BlockService.closePendingRequests/endFriendship` |
| SOC-09 engelli taraflar istek gönderemez (403 / 409), davet kodunda da, sayaç artmaz | `BlockControllerTest` | `FriendRequestService.send` |
| SOC-09 karşılıklı engel, engel kalkınca yeniden istek | `BlockControllerTest` | `UserBlockRepository.existsBetween` |
| SOC-09 eşzamanlılık: engel ↔ istek, engel ↔ kabul, çift engel | `BlockConcurrencyTest` | `UserPairLock` |
| SOC-10 kaldırma, yön, `404`, olay | `BlockControllerTest` | `BlockService.unblock`, `UserBlockRepository.deleteByBlockId` |
| SOC-11 liste, yalnız kendi, sayfalama, sınırlar | `BlockControllerTest` | `BlockService.listBlocked` |
| SOC-12 PUBLIC/FRIENDS/PRIVATE, kendi liste, fail-closed, engel, aynı yanıt | `FriendsVisibilityTest` | `FriendsListAccessPolicy` |
| SOC-12 adaptör yokken üretim davranışı | `FriendsVisibilityDefaultAdapterTest` | `UnavailableFriendsVisibility` |
| SOC-15 başka servis paketine/DB'sine/tablosuna bağımlılık yok, FK yok | `ServiceBoundaryTest` | — |
| Şema kısıtları (V124) | `FlywayMigrationTest` | `V124` |
| Olay yükü, `UserBlocked`/`UserUnblocked` | `BlockControllerTest`, `OutboxEventTest` | `domain/event/*` |
| Kimliksiz erişim `401` | `JwtValidationTest`, `BlockControllerTest`, `FriendsVisibilityTest` | `SecurityConfig` |

## 7. Açık noktalar

| # | Konu | Durum |
| --- | --- | --- |
| 1 | user-service'ten görünürlük tercihi okuyan gerçek adaptör (SOC-12) | user-service ucu + D4 bekleniyor; o zamana kadar başkasının listesi görünmez |
| 2 | messaging/lfp için `POST /internal/v1/blocks/check` ucu (SOC-15) | Sözleşme `docs/api.md`'de (PLANNED); D4 + güven modeli onayı sonrası yazılır |
| 3 | Hesap silme: `DELETE /internal/v1/accounts/{accountId}` | **Alıcı taraf yazıldı** (bkz. `TASK-AUTH-ENTEGRASYON.md`); çağıran Auth tarafı (adaptör + `svc+jwt` üreticisi) PENDING |
| 4 | `UserBlocked`/`UserUnblocked` ve diğer olayların gerçek teslimi | SOC-13 ile aynı: notification iç ucu + D4; olaylar outbox'ta |
| 5 | Engelleyen kullanıcının listesinde, onu engelleyen arkadaşların gizlenmesi | Kapsam dışı; gereksinim yok |
| 6 | Gateway'de social için oran sınırı | NOT IMPLEMENTED |
| 7 | `game-service` migration testleri `SCHEMA_MISMATCH` (Windows CRLF) | Hamza'ya bildirildi; tam kapıyı bu makinede etkiliyor |

## 8. Doğrulama durumu

| Kontrol | Sonuç |
| --- | --- |
| Birim + entegrasyon testleri (Testcontainers, gerçek PostgreSQL 17) | PASS — **167 test** |
| Mutasyon kontrolü (çift kilidi kaldırılınca yarış testi düşer) | PASS (test, kilidi gerçekten koruyor) |
| `.\pre-push\pre-push.cmd social-service` | PASS — `BACKEND TEST CHECK PASSED`, satır kapsamı **%95,82** |
| `.\pre-push\pre-push.cmd full dev` | **NOT SAFE TO COMMIT** — Git/Secret, Docker Configuration ve Backend HTTP Health (social V124 ile yeniden build edilip ayağa kalktı) PASS; Backend Tests/Build/Coverage FAIL → **yalnız `game-service` migration testleri** (`SCHEMA_MISMATCH`, Windows CRLF). social-service ve diğer 16 modül PASS |
| `.\sonarqube\sonar.cmd scan dev` | **NOT VERIFIED** — Sonar tüm modüllerin başarılı test raporlarını şart koştuğundan aynı engelle koşulmadı |

Tam kapıyı engelleyen hata TASK-02'dekiyle aynıdır ve social kodundan bağımsızdır (bkz. `docs-social/TASK-02.md` §1).
