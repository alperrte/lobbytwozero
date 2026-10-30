# TALEP 01 — `user` OLAY HEDEFİ, SERVİS TOKEN ÇİFTLERİ VE SİLME DURUMU

**Talep kodları:** USER-AUTH-REQ-01..04 · **Kimden:** Kerem (user-service) · **Kime:** Kerem (auth-service) · **Cevap tarihi:** 2026-10-30
**Durum:** REQ-01 AUTH HAZIR (şablon ve betik `user` ekler) · REQ-02 KARAR: B · REQ-03 KAPALI · REQ-04 AUTH HAZIR
**Kaynak:** `docs-user/integration/auth/auth-service-talepleri/auth-requests-01..04.md` (`feature/user-service`)

---

## REQ-01 — `AUTH_EVENT_TARGETS` değerine `user` eklenmesi

**Cevap: Auth kodunda iş yok, yalnız ortam ayarı.** Gönderici ile alıcı sözleşmesini karşılaştırdım; User'ın yazdığıyla uyuşuyor:

| Alan | Auth'un gönderdiği | Kanıt |
|---|---|---|
| Uç | `POST <USER_SERVICE_URL>/internal/events`; adres `deletion.service-urls.user` (varsayılan `http://user-service:8080`) | `HttpAuthEventSender` |
| Token | `typ=svc+jwt`, `sub=svc:auth-service`, `aud=user-service`, `scope=events:ingest`, `account_id=aggregateId`, 30 sn | `HttpAuthEventSender.send`, `ServiceTokenIssuer` |
| Zarf | `eventId, eventType, eventVersion, occurredAt, producer, aggregateId, payload` | `HttpAuthEventSender.body` |
| `UserRegistered` | payload `{accountId, username}` | `AuthEventRelay` |
| Yeniden yönlendirme | izlenmez; yalnız 2xx teslim sayılır, aksi halde yeniden dener | `HttpAuthEventSender` |

**Karar: `user` hedefi her ortamda varsayılan olarak açık olacak.** Bunu tek tek `.env` düzenlemeyle değil, ortak araçlarla yapıyoruz:
- `.env.example` artık `AUTH_EVENT_TARGETS=user` taşır (yeni kopyalar doğru başlar).
- `node ops/generate-auth-dev-keys.mjs` satır yoksa veya boşsa `user` yazar; dolu listeye (ör. `community`) dokunmaz, `user` yoksa uyarı basar (`ops/generate-auth-dev-keys.test.mjs`).
- Koddaki varsayılan hâlâ boş: üretim ortamı hedefi bilinçli ve ayrı açar (ortam değişkeni).

**Hedef User henüz ayağa kalkmamışken açılırsa ne olur:** Auth üstel geri çekilmeyle (10 sn–1 saat) yeniden dener, 20. denemede bir kez "takılı" uyarısı yazar; **olay kaybolmaz**. User ayağa kalkınca birikenler teslim edilir.
**Doğrulama:** Yeni hesap kaydı + e-posta doğrulama → `GET /api/v1/user/me` yanıtında `username` dolu. **NOT VERIFIED** (Auth ile gerçek User birlikte çalıştırılmadı).

## REQ-02 — Hedef açılmadan önce kayıt olmuş hesaplar

**Karar: B.** DEV'de hedef açılmadan önce kayıt olmuş hesaplar için geriye dönük yayın yapmıyoruz; User tarafında bu hesapların `username` alanı boş kalır.

Gerekçe:
1. Bu ortam henüz üretim değil. Gerçek kullanıcı hesabı yok; kalan yalnız geliştirme ve test hesapları.
2. REQ-01'in kararıyla `user` hedefi artık şablonda varsayılan açık. **Bundan sonra kayıt olan her hesabın `UserRegistered` olayı, User o an kapalı olsa bile outbox'ta bekler ve teslim edilir.** Boşluk yalnız hedefin açıldığı günden öncekiler için; yeni açığa yol açmaz.
3. A (geriye dönük yayın) Auth'a yeni bir migration (V69+) ve tek seferlik iş getirir; yalnız tek seferlik bir DEV boşluğu için kalıcı kod ekler. Auth'un çizgisi: kalıcı yüzeyi gerçek ihtiyaç doğurmadan büyütmemek.
4. C (`account:read` okuma ucu) **reddedildi:** User'a istemci gizli anahtarı ve hesap okuma yetkisi verir. Kullanıcı adını isteyen servis bunu olayla alır, Auth'a sormaz.

**User tarafı için pratik:** Eski DEV hesaplarını yeniden kaydedin veya DEV veritabanını sıfırlayın (kendi ortamınızda `docker compose down -v` yerel verileri siler, yalnız DEV'de). `username` boş profili kırılma değil beklenen durum olarak ele alın.
**Yeniden bakma koşulu:** Hedef açılmadan önce kayıt olmuş **gerçek** hesaplar varsa (üretim dahil) A seçeneği için ayrıca onay istenir; o zaman tek seferlik, idempotent bir yeniden yayın yazılır (yeni `eventId`, ACTIVE hesaplar, alıcı idempotent).

## REQ-03 — `DELETION_REQUESTED` hesabı bekleme süresinde token alabilir mi? (bilgi)

**Hayır.** Silme onayı gelince hesap `DELETION_REQUESTED` olur ve oturumlar iptal edilir. Doğru parolayla giriş bile `403 ACCOUNT_DELETION_PENDING` döner (`AccountDeletionGraceTest`). Eski access token Auth'ta `SessionStateValidator` ile hemen, diğer servislerde en çok 90 sn içinde ölür (60 sn token + 30 sn tolerans). Yani User bu hesabı normal kullanıcı olarak görmez, 90 sn'den sonra token'ı geçmez. Kullanıcı vazgeçerse (`/account/deletion/cancel`) hesap tekrar `ACTIVE` olur.

## REQ-04 — Servis token çiftleri

**Cevap: Auth tarafı hazır** (`application.properties`, testler, `servis-sozlesmeleri.md` §3.5, `docs/api.md`):

| Çağıran (`sub`) | Hedef (`aud`) | Kapsam | Gizli anahtar (Auth ortamı) |
|---|---|---|---|
| `svc:social-service` | `user-service` | `privacy:read`, `events:ingest` | `AUTH_SOCIAL_SERVICE_CLIENT_SECRET` (mevcut) |
| `svc:library-service` | `user-service` | `privacy:read`, `events:ingest` | `AUTH_LIBRARY_SERVICE_CLIENT_SECRET` (yeni) |
| `svc:hardware-service` | `user-service` | `privacy:read` | `AUTH_HARDWARE_SERVICE_CLIENT_SECRET` (yeni) |
| `svc:review-service` | `user-service` | `events:ingest` | `AUTH_REVIEW_SERVICE_CLIENT_SECRET` (yeni) |

Notlar:
- **Kapsamlar çifte bağlıdır:** hardware `events:ingest` alamaz, review `privacy:read` alamaz. `privacy:read` kullanıcıya bağlı değildir; görüntüleyen ve sahip istek gövdesindedir. Karar User'ındır, Auth izni kanıtlamaz.
- **Tek yön:** User dış servisi senkron çağırmadığı için Auth'ta `user-service` istemcisi yoktur (daha önce önerdiğimiz `friends:check` kapsamı ve `user-service → social-service` çifti bu yüzden kaldırıldı).
- **Gizli anahtarlar:** hepsi boşsa istemci kapalı. `node ops/generate-auth-dev-keys.mjs` eksik olanları üretir. Aynı değerin ilgili servislerin ortamına verilmesi o servis sahiplerinin işidir; ben hiçbir ortam dosyasına dokunmadım.
- **Kabul:** DEV'de bir servis token'ıyla karar ucu `200`, kapsamsız token `403` olmalı. Auth tarafında çiftleri `ServiceAudienceTokenTest` ve `ops/auth-smoke.mjs` doğruluyor; User ucu ile uçtan uca **NOT VERIFIED**.

## STEAM BAĞLAMA (Faz 3, henüz dosyası yok)

Auth'ta bugün yalnız Steam ile giriş/kayıt var; oturumdaki hesaba bağlama/kaldırma ucu ve `ExternalIdentityLinked/Unlinked` olayı yok. Talep dosyası gelince ayrıca cevaplanır (son giriş yöntemi koruması dahil). Şimdiden: bu bir **Auth geliştirmesi** olacak, kimlik kanıtı yalnız Auth'ta üretilir.
