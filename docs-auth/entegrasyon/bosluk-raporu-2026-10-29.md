# BOŞLUK RAPORU — AUTH ENTEGRASYONU

**Tarih:** 2026-10-29 · **Kapsam:** 17 servis, Gateway ve frontend. Her servisin belgesi, `docs/api.md` kaydı ve kodu Auth sözleşmesiyle karşılaştırıldı
**Nasıl yapıldı:** 5 salt-okunur keşif (Alper, Hamza, Nisa, Umut grupları ve ortak servisler), kritik iddialar sonra koddan elle doğrulandı

---

## KISA SONUÇ

- **Auth'un verdikleri tamam:** JWT ve JWKS, roller, hesap silme koordinasyonu, servis token'ı ve izin listesi, admin iç uçları. Hiçbir ekibin belgesinde Auth'ta bulamadığı bir kullanıcı ucu çıkmadı
- **Boş bıraktığımız 3 şey vardı** (masaüstü cihaz kimliği, hesap durumu olayı, güvenlik ve durum e-postaları). **2026-10-29 itibarıyla Auth tarafında hepsi uygulandı**; karşı servislerin işleri aşağıdaki tabloda.
- **Başka ekiplerin kodunda 3 gerçek sorun çıktı:** Community kapsam adı, Social silme ucu, Community giden çağrıları
- **Çoğu servis iskelet:** 11 servisin silme ucu yok, bu yüzden hesap silme global olarak tamamlanamaz

---

## DOĞRULANAN BULGULAR (koddan teyitli)

| # | Bulgu | Kanıt | Kimin |
|---|---|---|---|
| G1 | Community olay ucu `SCOPE_internal.events` istiyor, Auth `events:ingest` veriyor. Gerçek doğrulayıcı bağlanınca Social ve Admin token'ları **403** alır | `community-service/.../SecurityConfig.java:30`, `docs/api.md` Community olay ucu | Nisa, tek satır |
| G2 | Social silme ucu `sub` ve `account_id`'ye hiç bakmıyor. 60 sn içinde bir hesabın silme token'ı başka hesabı silmekte kullanılabilir | `social-service/.../AccountErasureController.java` (yalnız `erasure.erase(accountId)`) | Umut. Game doğru yapıyor, örnek al |
| G3 | Community'nin JWT doğrulayıcısı hâlâ her token'ı reddediyor, giden çağrılarda (admin, notification) token yok, `roles` eşlemesi yok | `community-service/.../SecurityConfig.java:44-57`, `EventDeliveryClient` | Nisa |

---

## AUTH'TA OLMAYAN, BİZİM YAZACAĞIMIZ ŞEYLER

| # | Eksik | Kim bekliyor | Durum | Önerilen |
|---|---|---|---|---|
| A1 | **Masaüstü cihaz kimliği (B yolu)** | Hardware, Performance | Karar B, **Auth'ta uygulandı (2026-10-29)** | `docs/api.md` "Masaüstü cihaz kimliği"; Hardware'de `dev+jwt` doğrulayıcısı bekliyor |
| A2 | **`AccountStatusChanged` olayı** (D3) | Community, Game, diğerleri | **Auth'ta hazır (2026-10-29):** transactional outbox; yalnız yönetici durum değişikliğinde çıkar, hedef listesi varsayılan boş | Alıcı uç yazılınca `ltz.auth.events.targets`'a eklenir |
| A3 | **Güvenlik e-postaları (MAIL-03) ve hesap durumu e-postaları (MAIL-04)** | Mail servisi, kullanıcılar | **Auth'ta hazır (2026-10-29):** `SecurityNotificationMailPort`; parola, e-posta ve durum bildirimleri. Prod'da Mail ucu gelene kadar kapalı (fail-closed). Borç B12 kısmen kapandı | Mail Service bildirim sözleşmesi ve adaptörü |
| A4 | **`UserRegistered` ve e-posta değişti olayı** | user-service | **Auth'ta hazır (2026-10-29):** outbox, `UserRegistered` ve `AccountEmailChanged`; hedef listesi varsayılan boş | user-service alıcı ucu yazılınca `user` hedefi eklenir |
| A5 | **`blocks:check` için istemci kayıtları** (messaging, lfp → social) | Messaging, LFP, Social | **Auth'ta hazır (2026-10-29):** iki istemci `audience: "social-service"` ile `blocks:check` token'ı alıyor | Social ucunun yazılması |
| A6 | **Tanımsız çiftler:** admin → review (REV-07/15), content/event → notification, admin, mail | Review, Content, Event | Sözleşme yok | Önce ihtiyaç yazılsın, sonra karar |

---

## BAŞKA EKİPLERİN YAPACAKLARI

| # | İş | Sahip | Etki |
|---|---|---|---|
| T1 | Community: kapsam adı `SCOPE_events:ingest`, gerçek decoder, `ROLE_` eşlemesi, giden token, silme ucu | Nisa | Hesap silme tamamlanır, olay akışı çalışır |
| T2 | Social: silme ucunda `sub` tam eşleşme ve `account_id` denetimi, olay için token alma | Umut | Güvenlik açığı kapanır |
| T3 | Silme ucu yazılacak servisler: user, mail, messaging, lfp, community, content, event, recommendation, performance, admin, notification | İlgili sahipler | 16 hedefin hepsi onaylamadan hesap silme tamamlanmaz |
| T4 | Rol kararı: resmi duyuru ve etkinlik oluşturma için hangi rol (`ADMIN`, `MODERATOR`, `CONTENT_CREATOR`) | Nisa | Content ve Event yazılabilir |
| T5 | Hardware: `dev+jwt` doğrulayıcı (A1 bitince). Performance: PERF-01 aktif cihaz API'si | Alper | Masaüstü senkronu açılır |
| T6 | Frontend: Auth çağrıları yok (`csrf`, `X-XSRF-TOKEN`, 401'de `session/refresh`, `ProblemDetail.code`) | Frontend | Giriş akışı çalışır |
| T7 | Bayat belgeler: GAME-04 "Auth token ömrü 5 dk" (artık 60 sn). Bizim `03_HAMZA` dosyasındaki "Uç yok" satırları da artık yanlıştı, düzeltildi | Hamza, Kerem | Karışıklık biter |

---

## SORULARA CEVAP (keşiften çıkan açık sorular)

- **Auth `/api/v1/auth/**` POST uçlarında CSRF'i kendisi uygular** (`SecurityConfig`, `CookieCsrfTokenRepository`). Gateway'in bu yolları çerez dönüşümünden atlaması sorun değil
- **Hamza'nın servisleri Auth'tan başka bir uç istemiyor.** Yeni bir istemci kaydı gerekmedi
- **`hardware:sync:write`:** Auth scope'u olarak önerilir, Hardware'in izin dizesi zaten bu (`HardwareSyncDeviceAccess.PERMISSION`)
- **`AccountDeleted` olayı:** Community raporu bekliyor, Auth bunu olay olarak yayınlamıyor, silmeyi senkron `DELETE` ucuyla yapıyor. Community için `DELETE` yeterli, olay gerekmiyor

## KARAR BEKLEYENLER (Kerem)

1. ~~A1 cihaz kimliği tasarımı~~ yapıldı (2026-10-29)
2. ~~A5 `blocks:check` istemcileri~~ yapıldı (2026-10-29)
3. ~~A2 `AccountStatusChanged`~~ yapıldı (2026-10-29, outbox)
