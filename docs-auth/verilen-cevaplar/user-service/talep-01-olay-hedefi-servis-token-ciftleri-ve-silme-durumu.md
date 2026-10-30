# TALEP 01 — `user` OLAY HEDEFİ, SERVİS TOKEN ÇİFTLERİ VE SİLME DURUMU

**Talep kodları:** USER-AUTH-REQ-01..04 · **Kimden:** Kerem (user-service) · **Kime:** Kerem (auth-service) · **Cevap tarihi:** 2026-10-30
**Durum:** REQ-01 AUTH HAZIR (ortam ayarı bekliyor) · REQ-02 KARAR BEKLİYOR · REQ-03 KAPALI · REQ-04 AUTH HAZIR (PR #36)
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

**Yapılacak (ortam, Kerem):** DEV `.env` içinde `AUTH_EVENT_TARGETS=user` (Community alıcı ucu da hazırsa `community,user`), sonra `docker compose up -d auth-service`. Bu repoda değiştirmedim; ENV sende.
**Doğrulama:** Yeni hesap kaydı + e-posta doğrulama → `GET /api/v1/user/me` yanıtında `username` dolu. **NOT VERIFIED** (Auth ile gerçek User birlikte çalıştırılmadı).

## REQ-02 — Hedef açılmadan önce kayıt olan hesaplar

**Öneri: B (DEV'i sıfırla veya hesapları yeniden aç).** Gerekçe: ortam henüz üretim değil, A için yeni migration veya tek seferlik iş gerekir ve bu, `UserRegistered` bilgisinin eski hesaplar için yeniden üretilmesi demektir (kullanıcı adı kopyası). Üretimde ACTIVE hesap yoksa A'ya değmez.

- **A seçilirse (Auth işi):** `auth_event_outbox`'a ACTIVE hesaplar için yeni `eventId`'li `UserRegistered` satırları yazan tek seferlik bir yol. Yeni migration V69+ gerekir; ayrıca onay istenir.
- **C (`account:read` okuma ucu):** Reddediyorum. User'a istemci gizli anahtarı ve hesap okuma yetkisi verir; Auth'un çizgisi "olay yoluyla, yetki genişletmeden".

**Karar Kerem'de:** B mi A mı? Üretimde gerçek hesap var mı?

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
