# Admin Talebi 01 — Mesajlaşma moderasyon sözleşmesinin tanımlanması (MSG-27)

> Tarih: 2026-11-20 · Gönderen: Umut (messaging-service) → `admin-service`
> Sorumlu geliştirici: **atanmamış.** Admin ortak servistir (`AGENTS.md` §1); belgelerde sahip yazılı değil. Değişiklikten önce sorumluluğun kimde olduğu belirlenmelidir.
> Durum: **TALEP / KARAR İSTENİYOR.** Bu belge bir uç **dayatmaz**; Messaging'de hazır bir moderasyon ucu yoktur. Önce sahip ve kapsam kararı, sonra sözleşme istenir. §3 öneridir.
> Messaging gereksinimi: **MSG-27** ("yetkili moderasyon akışları için `admin-service` ile sözleşme bazlı entegrasyon").
> İlgili: [Community → Admin talebi 01](../../../docs-community/admin-service/talep/req-01.md) (`ContentReported` / `ModerationActionTaken` ortak zarfı).

## 1. İhtiyaç

MSG-27, moderasyon için admin-service ile sözleşme gerektirir; ancak şu an sözleşme yok. Cevaplanması gereken sorular:

1. Mesajlaşmada moderasyon **kapsamı** nedir? (ör. kullanıcı şikâyeti üzerine mesajın incelenmesi; kullanıcının mesajlaşmadan men edilmesi; sohbetin kapatılması.)
2. Özel mesaj içeriği moderatöre hangi koşulda açılır? (Mesajlar gizli veridir; SECURITY.md §6.) Yalnız şikâyet edilen mesaj ve çevresi mi, hangi denetim kaydıyla?
3. Mesaj şikâyeti kullanıcı arayüzünde var mı? Bugün Messaging'de şikâyet ucu yok.

## 2. Karşı servisin mevcut desteği

Doğrulanan durum (2026-11-20, `origin/main`):

- `backend/services/admin-service` içinde yalnız `AdminServiceApplication` ve `AdminServiceApplicationTests` vardır; alıcı uç, vaka/kuyruk, denetim kaydı yoktur. `docs/api.md` ADMIN bölümü kayıtsızdır.
- `docs/architecture.md` §6.3: `ContentReported` üreticileri `review, community, content, hardware, lfp`; **messaging listede yok**. `ModerationActionTaken` tüketicileri "ilgili kaynak servis".
- Auth'ta `messaging-service ↔ admin-service` çifti **tanımlı değil** (şu an yalnız `community-service → admin-service` ve `admin-service → community-service`).

## 3. Öneri (karar sonrası)

Kapsam "mesaj şikâyeti + kullanıcıyı mesajlaşmadan men" olarak kararlaştırılırsa, Community ile **aynı zarf ve alıcı kuralları** yeniden kullanılır:

| Yön | Olay / uç | Not |
| --- | --- | --- |
| Messaging → Admin | `ContentReported` v1, `POST {ADMIN_SERVICE_URL}/internal/events` | Yük yalnız kimlik: `messageId`, `conversationId`, `reporterId`, `reportedUserId`, neden kodu. **Mesaj metni taşımaz.** Katalogda messaging üretici olarak eklenmeli |
| Admin → Messaging | `ModerationActionTaken` v1, `POST {MESSAGING_SERVICE_URL}/internal/events` | Mesajı gizle / kullanıcıyı mesajlaşmadan men. Messaging kararı uygular |
| Admin → Messaging | İçerik okuma: `GET /internal/v1/moderation/messages/{messageId}` (ÖNERİ) | Yalnız şikâyet edilmiş mesaj ve sınırlı bağlam; her çağrı Admin denetim kaydına yazılır; yeni kapsam (`moderation:read`) |

Kimlik: Auth servis token'ı (`typ=svc+jwt`, `events:ingest` / `moderation:read`); Auth çiftleri için Kerem'e ayrı talep (`docs-auth/verilen-cevaplar/messaging-service/`) kararla birlikte açılır.

## 4. Messaging tarafında hazırlık

Hazır altyapı: outbox + relay (`OutboxRelay`, `FOR UPDATE SKIP LOCKED`, geri çekilme), servis token istemcisi. Yok: şikâyet ucu, mesaj gizleme durumu için moderasyon alanı (mevcut "silindi" durumundan ayrı bir moderasyon durumu gerekebilir), men listesi. Bunlar kararı takip eden **ayrı bir Messaging görevidir**; şimdi yazılmadı.

## 5. Kabul kriteri (bu talep için)

Admin sorumlusu atanır ve §1'deki üç soruya yazılı cevap verir. Cevap "mesaj moderasyonu kapsam dışı" ise MSG-27'nin admin kısmı o kararla kapatılır ve gereksinim notuna işlenir.
