# USER-SERVICE — AUTH ENTEGRASYONU

**Sahip:** Kerem · **Kod durumu:** İskelet · **Güncelleme:** 2026-10-29

## ÖZET

User Service profil sahibi, Auth kimlik sahibi. Aralarındaki bağ iki olay (hesap oluştu, e-posta değişti) ve hesap silme. Olaylar Auth tarafında **hazır** (2026-10-29, transactional outbox); hedef listesi varsayılan boş, User'ın alıcı ucu yazılınca `user` hedef olarak eklenir.

## AUTH'TAN HAZIR OLANLAR

- Kullanıcı JWT: `README.md` bölüm 1. Kullanıcı adı ve e-posta token'da yok, Auth'ta saklı
- Hesap silme çağrısını Auth yapar, `DELETE /internal/v1/accounts/{id}` ucunu user-service açar: `README.md` bölüm 2
- Auth'ta e-posta değiştirme ucu var: `POST /api/v1/auth/email/change` ve `/confirm`

## SENİN YAPACAKLARIN

- [ ] JWT doğrulayıcı (JWKS)
- [ ] Silme ucu. Profil ve profil görselleri silinir, kimlik bağı kalmaz. Yazılmadıkça User görevi `PENDING`
- [ ] Karar: kullanıcı adı ve görünen ad **hangi serviste duruyor**. Auth'ta `username` var ve herkese açık. Önerim: Auth kimlik kaynağı, User `userId` ile profil sahibi, kullanıcı adını Auth'tan bir kez alıp kopyalamak yerine her seferinde sormamak için olay ile eşitle

- [x] Gizlilik kararı ve özet olayları: User'ın `POST /internal/v1/privacy/decisions` ve `POST /internal/events` uçları hazır (REQ-04). Çağıranlar Auth'ta tanımlı: social (`privacy:read`, `events:ingest`), library (`privacy:read`, `events:ingest`), hardware (`privacy:read`), review (`events:ingest`). Alıcı denetimi User'ındır: `typ`, `aud=user-service`, kapsam, izinli çağıran listesi, olay türü ↔ `sub`
- User Service **istemci değildir**: kimseyi senkron çağırmaz, Social/Library/Review verisi olayla gelir. Bu yüzden Auth'ta `user-service` istemcisi ve `friends:check` kapsamı yoktur
- [ ] Profil sayfasında Library'yi kullanıcı adına çağırırsan (LIB-10) o kullanıcının kendi `at+jwt`'sini ilet; saklama, loglama, arka planda kullanma. Auth kullanıcıyı taşıyan servis token'ı üretmez. User kimseyi çağırmadığı için bu yol seçilirse karar gerekir

## AUTH'TA BEKLEYEN

- `UserRegistered` olayı (ACTIVE olunca): **Auth'ta hazır** (2026-10-29). Gövde `eventId`, `eventType`, `eventVersion`, `occurredAt`, `producer`, `aggregateId`, `payload`; payload kullanıcı adıdır, e-posta yoktur. Alıcı: `POST /internal/events`, `aud=user-service`, `typ=svc+jwt`, `scope=events:ingest`, `sub=svc:auth-service`. Alıcı uç hazır (User dalı); dev şablonunda `AUTH_EVENT_TARGETS=user` varsayılandır ve `node ops/generate-auth-dev-keys.mjs` eksik ortamlara ekler (yeni (çağıran, hedef) çifti gerekmez, Auth kendi adıyla gönderir)
- E-posta değişti olayı (`AccountEmailChanged`, borç B19 kısmen kapandı): **Auth'ta hazır** (2026-10-29), aynı yol. Olay yeni adresi taşımaz (e-posta kişisel veri, olaylara konmaz). Profilde e-posta tutacaksan yeni adresi kullanıcıdan ya da kendi akışından al; Auth'ta User için bir hesap-okuma izni tanımlı değil, gerekirse `talep` yaz
- `AccountStatusChanged` (D3): Auth'ta hazır (2026-10-29), yalnız yönetici durum değişikliğinde çıkar
- USER-07 (Steam ve Discord bağlantısı): Auth'ta Steam ve Google girişi var (kimlik kanıtı), Discord yok. Auth'ın bağlı hesap bilgisini User'a vermesi için sözleşme yazılı değil

## İLGİLİ BELGELER

`docs-auth/verilen-cevaplar/user-service/`
