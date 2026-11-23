# LFP Service — Entegrasyon İşlemlerinin Tamamlanması (TASK-04)

> Tarih: 2026-11-17 · Sorumlu: Umut · Servis: `lfp-service` · Dal: `feature/lfp-service` (üzerine `origin/main` commit'siz alındı) · Kapsam: LFP-16, LFP-17, LFP-18 entegrasyonlarının gerçek servislerle doğrulanması · Durum: **canlı doğrulama PASS (74/74); kod değişikliği gerekmedi**
>
> Önceki görevler: [TASK-01](TASK-01.md), [TASK-02](TASK-02.md), [TASK-03](TASK-03.md). Bu görev yeni özellik eklemez; TASK-03'te "NOT VERIFIED" kalan servisler arası akışları DEV yığınında gerçek servislerle doğrular, belgeleri günceller ve açık noktaları sahiplerine iletilecek biçimde yazar.

## 1. Sonuç

| Entegrasyon | Karşı servis | Durum | Kanıt |
| --- | --- | --- | --- |
| Kullanıcı JWT (JWKS, `at+jwt`) | auth-service | **PASS** | Kimliksiz `401`; ilan `ownerId` = token `sub`; başkasının ilanını güncelle/kapat `403` |
| Oyun doğrulama | game-service | **PASS** | Gerçek oyun ile `201`; olmayan oyun `422` (kimliksiz kart ucu) |
| Engel sorgusu, iki yönde | social-service (+ Auth servis token'ı `aud=social-service`, `blocks:check`) | **PASS** | A→B engelinde B'nin listesinde ilan yok, detay/iletişim `404`; ters yönde (B→A) detay `404`; engelsiz kullanıcı görür; engel kalkınca ilan döner |
| Fail-closed | social-service kapalı | **PASS** | Liste/detay `503`; "kendi ilanlarım" `200`; Social dönünce `200` |
| İlan sahibiyle iletişim | messaging-service | **PASS** | `contact` → `recipientId` = sahip; kendi ilanına `400`; `POST /messaging/conversations/direct` yeni sohbet `201`, tekrar `200`, aynı sohbet kimliği, üyeler {A, B} |
| Hesap silme (Auth → LFP) | auth-service | **PASS** | Gerçek `svc+jwt` ile Auth'taki `lfp` görevi `CONFIRMED`; `lfp_db`'de hesabın ilanları 0 |
| Gateway | api-gateway | **PASS** | Tüm kullanıcı çağrıları Gateway (5080) üzerinden |
| user-service | — | Çağrı yok (tasarım) | İstemci sahibin adını `user-service` toplu profil kartından alır (TASK-03 §3) |
| notification-service | — | **BEKLİYOR** | Serviste hiç uç yok (iskelet); bildirim sözleşmesi tanımlı değil |
| `AccountStatusChanged` | auth-service | **KAPSAM DIŞI** | Kullanıcı kararıyla bu göreve alınmadı (bkz. §4) |

Doğrulama (bu görevde):

| Kontrol | Sonuç |
| --- | --- |
| Canlı uçtan uca (Gateway → gerçek Auth, Social, Messaging, Game, LFP, Mail) | **74/74 PASS**, 0 FAIL |
| Derleme/test | Bu görevde LFP koduna dokunulmadı; TASK-03 §8 sonuçları geçerli |
| SonarQube ikinci tarama (lfp-service) | Yapılmadı (TASK-03 §8 açık nokta olarak duruyor) |

## 2. Yöntem

- Ortam: `ltz-dev` yığını — postgres, mailpit, mail, auth, user, game, social, messaging, lfp, api-gateway. İlgili 8 imaj `origin/main` merge'ünden sonra yeniden derlendi (paralellik 3).
- Üç gerçek test hesabı (A ilan sahibi, B görüntüleyen, C başkası) Gateway üzerinden kayıt + e-posta doğrulaması + giriş yaptı; oyun fixture'ı `game_db`'ye eklenip bitince silindi.
- Akışlar: oluşturma/Game doğrulama → okuma ve sahiplik → iletişim izni ve Messaging'de sohbet → iki yönde engel → Social'ı durdurma (fail-closed) → Auth hesap silme (istek + e-posta token'ı + onay) ve `account_deletion_tasks` / `lfp_db` kontrolü.
- Script depoya alınmadı (geçici). Gerekirse `ops/` altında kalıcı e2e olarak eklenebilir (ekip alanı; ayrı karar).

## 3. Doğrulama sırasında karşılaşılanlar (ortam, kod değil)

| Durum | Ne yapıldı |
| --- | --- |
| auth-service açılışta Flyway checksum uyuşmazlığı (V61–V67; main'den gelen değişiklik) | `docs-auth/migration-yazim-kurallari.md` prosedürü: önce `pg_dump` yedeği, sonra yalnız yerel `auth_db`'de `DROP SCHEMA public CASCADE; CREATE SCHEMA public;`. `flyway repair` kullanılmadı. Yerel Auth hesapları sıfırlandı (`node ops/dev.mjs accounts` ile yeniden oluşturulur) |
| `.env.dev` gerçek e-posta modunda (Gmail SMTP); doğrulama kodu Mailpit'e düşmüyordu | `.env.dev` değiştirilmedi. Yalnız bu oturumda `MAIL_SMTP_*` kabuk değişkenleri boş verilerek mail-service Mailpit'e yönlendirildi. İlk denemede bir doğrulama e-postası gerçek SMTP'ye `@example.test` adresine gitmeye çalıştı (teslim edilemez) |
| Hesap silme bekleme süresi 14 gün | Yalnız bu oturumda `AUTH_DELETION_GRACE_PERIOD=PT0S`, poll `PT2S` kabuk değişkeniyle verildi; dosya değişmedi. Bu ayarlı auth-service konteyneri iş bitince durduruldu |
| Test betiğinin rastgele parolası bir kez `WEAK_PASSWORD` aldı | Betikte yeniden deneme eklendi (servis davranışı doğru) |
| İlk betikte Messaging yeni sohbet için `200` bekleniyordu; sözleşme `201` (yeni) / `200` (mevcut) | Beklenti düzeltildi, ikinci çağrı `200` olarak ayrıca doğrulandı |

## 4. Açık noktalar ve sahiplere iletilecekler

1. **`AccountStatusChanged` (kapsam dışı).** Auth olay hedefi varsayılan boş; LFP'de `POST /internal/events` yok. Askıya alınan kullanıcının açık ilanları süresi dolana kadar (en çok 7 gün) görünür. İleride: LFP'de iç olay ucu + Auth'ta `AUTH_EVENT_TARGETS`'a `lfp` (Kerem ile).
2. **notification-service** (ortak, iskelet): alıcı uç ve olay sözleşmesi yok. LFP şimdilik bildirim üretmez; sözleşme tanımlanınca "ilana iletişim talebi" olayı değerlendirilir.
3. **`docs-auth/entegrasyon/README.md`** tablosunda lfp-service satırı "İskelet"; dosya Kerem'indir, güncellenmesi önerilir ("Aktif, uçtan uca doğrulandı 2026-11-17").
4. **SonarQube** ikinci tarama (lfp-service'te düzeltilen 2 bulgunun doğrulanması) ve depo geneli Quality Gate (LFP dışı bulgular) merge öncesi.

### Sahiplere kısa mesaj taslakları

- **Kerem (Auth):** "LFP, Auth'un gerçek servis token'ıyla DEV'de doğrulandı: `lfp-service` istemcisi `aud=social-service, scope=blocks:check` alıyor; hesap silmede `lfp` görevi ilk turda `CONFIRMED`. `docs-auth/entegrasyon/README.md` içindeki lfp-service satırını 'İskelet' yerine 'Aktif, uçtan uca doğrulandı' yapabilir misin? Ayrıca main'deki `auth_db` Flyway checksum değişikliği yerel veritabanlarında sıfırlama gerektiriyor; bunu ekibe duyurmak iyi olur. `AccountStatusChanged` için LFP'ye olay hedefi şimdilik istemiyoruz."
- **Notification sahibi (ortak):** "LFP bildirim üretmek isterse notification-service'te alıcı uç (`POST /internal/events`) ve olay şeması gerekiyor. Şu an serviste uç yok. Bildirim sözleşmesi netleşince LFP'den 'ilana iletişim' olayı için talep açacağız."
- **Social ve Messaging (kendi servislerim):** Ek talep yok; `blocks:check` sözleşmesi ve direkt sohbet ucu LFP ile canlı doğrulandı.

## 5. Değişen dosyalar

- `docs/api.md` (LFP: `contact` ve hesap silme kayıtlarının test durumu), `docs-auth/entegrasyon/lfp-service.md`, `docs-lfp/TASK-03.md` (NOT VERIFIED → PASS), `docs-lfp/TASK-04.md` (yeni).
- Kod, migration ve ENV dosyası değişmedi. `origin/main` merge'ü commit'siz staged durumda (commit Umut'ta).
