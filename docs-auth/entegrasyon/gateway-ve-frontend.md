# API-GATEWAY VE FRONTEND — AUTH ENTEGRASYONU

**Sahip:** Alper (Gateway) · Frontend ekibi · **Güncelleme:** 2026-10-29

## API-GATEWAY

**Durum: tamam, Auth ile uyumlu ve canlı denendi** (gerçek yığında Gateway üzerinden kayıt, giriş, silme iptali ve servis rotaları).

- `/api/v1/auth/**` Auth'a yönlenir. 16 servis rotası var. `/internal/**` için rota **yok**, doğru: Gateway'den `POST /internal/v1/service-tokens` 404 döner (denendi)
- Çerezi Bearer'a çevirir (Auth hariç), CSRF ve Origin doğrular, istemciden gelen `x-internal-*` başlıklarını temizler, Auth uçlarına IP başına dakikalık hız sınırı uygular
- `/api/v1/auth/**` yolları çerez dönüşümünden atlanır. Bu sorun değil: **Auth CSRF'i kendi tarafında uygular** (`SecurityConfig`, `CookieCsrfTokenRepository`)
- Açık: çok örnekli (birden fazla Gateway) ortak hız sınırı yok, tek örnek içinde bellekte

## FRONTEND

**Durum: Auth çağrısı henüz yok** (`frontend/src` içinde Auth kodu bulunmadı, `client.ts` yalnız `credentials` ekliyor). Auth'ta frontend'in beklediği hiçbir uç eksik değil. Tüm uçlar `docs/api.md`, `## AUTH`.

### Yapılacaklar (sırayla)

- [ ] İlk istekten önce `GET /api/v1/auth/csrf` (`204`), sonra durum değiştiren tüm isteklerde `X-XSRF-TOKEN` başlığı ve `credentials: "include"`
- [ ] Kayıt, e-posta doğrulama (6 haneli kod), giriş, 2FA, çıkış ekranları (`features/auth`)
- [ ] `401` alınca `POST /api/v1/auth/session/refresh`, başarılıysa isteği **bir kez** tekrarla (access token 60 sn yaşar, bu akış şart)
- [ ] Hata yanıtındaki `ProblemDetail.code` alanını taşı ve ekranda eşle (`ApiHttpError` şu an yalnız `status` tutuyor). Kod listesi: `docs/api.md` (her ucun "Hata Durumları" bölümü) ve `docs/EKIP_AUTH_GATEWAY_ENTEGRASYON_NOTLARI.md`
- [ ] Google ve Steam girişi: Auth `FRONTEND_PUBLIC_URL/auth/callback` adresine sonucu **fragment** ile döner (`#status=...`). `/auth/callback` sayfası gerekir. Steam akışında `registration_required` ve `email_verification_required` durumları var
- [ ] Parola sıfırlama, hesap silme (14 gün geri alma süresi: kullanıcı bu sürede giriş yapamaz ve vazgeçme ekranı gerekir: `POST /api/v1/auth/account/deletion/cancel`), e-posta değiştirme ekranları
- [ ] Parola kuralı: en az 15 karakter. Sunucu `WEAK_PASSWORD` döner, ekranda göster

### Giriş yapılamayan hesaplar

`ACCOUNT_DELETION_PENDING` (silme bekleme süresinde), `ACCOUNT_SUSPENDED`, `ACCOUNT_BANNED`, `EMAIL_NOT_VERIFIED` kodlarını ayrı mesajla göster. Silme bekleyen hesap vazgeçebilir, diğerleri vazgeçemez.

## İLGİLİ BELGELER

`docs/EKIP_AUTH_GATEWAY_ENTEGRASYON_NOTLARI.md` (cookie, CSRF ve giriş akışlarının ayrıntısı), `docs/api.md` (`## AUTH`)
