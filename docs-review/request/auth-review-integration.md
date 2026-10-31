# AUTH–REVIEW entegrasyon talebi

## 1. Talep eden servis

`review-service` — Hamza. Bu belge Review'ın ihtiyacını bildirir; Auth sahibine kesin geliştirme emri değildir.

## 2. Hedef servis

`auth-service` — Kerem. Nihai yetki/istemci tasarımı Auth sahibince belirlenir.

## 3. İlgili task

REV-07 ve REV-10. Bildirim üreticisi Review seçilirse, aşağıdaki koşullu bildirim yetkisi de REV-10 ile ilişkilidir. REV-06 bu talepten bağımsızdır.

## 4. Talebin amacı

Gelecekte onaylanacak rapor teslimi ve moderasyon geri bildirimini, kullanıcı token'ını servis kimliği yerine kullanmadan, hedefe bağlı ve en az yetkili servis token'ıyla gerçekleştirmek.

## 5. Repository üzerinde doğrulanan mevcut durum

- [Auth application.properties](../../backend/services/auth-service/src/main/resources/application.properties): `review-service` istemci kaydı ve `${AUTH_REVIEW_SERVICE_CLIENT_SECRET:}` referansı var; **yalnız** `targets.user-service=events:ingest` tanımlı. Gerçek secret değeri/dağıtımı incelenmedi.
- Aynı dosyada community→admin ve community→notification `events:ingest` örnekleri var; bunlar Review'a yetki vermez. Admin'in mevcut hedefi community; admin→review tanımlı değil.
- [ServiceClientConfig](../../backend/services/auth-service/src/main/java/com/ltz/auth/infrastructure/config/ServiceClientConfig.java): hedef kapsam allowlist'i `events:ingest`, `blocks:check`, `privacy:read`, `signals:read`; tanımsız özel kapsam uydurulamaz. `account:erase` ayrı D4 sözleşmesidir ve bu iş için kullanılamaz.
- Mevcut `POST /internal/v1/service-tokens`, hedef audience seçimini destekler. Kayıtsız hedef 403, geçersiz istemci 401 davranışı [Auth servis sözleşmesinde](../../docs-auth/servis-sozlesmeleri.md) belgeli.
- Kullanıcı JWT/JWKS ve Auth→Review D4 silme entegrasyonu zaten mevcut; yeniden kurulması talep edilmiyor.

## 6. Eksik gereksinimler

- Admin ile alıcı protokolü onaylanınca Review'ın yalnız rapor olayını Admin'e iletebilmesine uygun hedef/kapsam çifti.
- Admin'in onaylı rapor durum/karar mesajlarını yalnız Review'a iletebilmesine uygun ters hedef/kapsam çifti.
- **Koşullu:** moderasyonun uygulandığı bilgisini Notification'a Review gönderecekse Review→Notification için ayrı hedef/kapsam. Üretici Admin seçilirse gereksiz Review izni verilmemeli.
- Mevcut Review→User izni korunmalı; bu taleple genel admin yetkisi, başka hesap okuma veya tüm hedeflere erişim istenmiyor.

## 7. Önerilen sözleşme — kesinleşmedi

Yeni token endpoint'i yerine mevcut token ucunun kullanılması önerilir. Kavramsal istek: `clientId=review-service`, bellekte taşınan mevcut client secret, `audience=admin-service`. Gerçek secret belgeye/komut satırına yazılmaz.

Önerilen en dar çiftler:

- review-service → admin-service: alıcı olay modeli seçilirse `events:ingest`.
- admin-service → review-service: geri bildirim olay modeli seçilirse `events:ingest`; Review ayrıca tam producer/olay türü allowlist'i uygular.
- review-service → notification-service: yalnız üretici kararı Review ise `events:ingest` önerisi.

Bunlar bugün yapılandırılmış izinler değildir. Senkron komut veya durum sorgusu seçilirse farklı kapsam gerekip gerekmediğine Auth ve hedef servis sahipleri karar verir; `events:ingest` otomatik okuma/moderasyon yetkisi sayılmaz. Mevcut response alanları `accessToken`, `tokenType`, `expiresIn`, `scope` üzerinden kalabilir; yeni token claim'i dayatılmıyor.

## 8. Kimlik doğrulama ve yetkilendirme beklentileri

Mevcut D3 modeliyle uyum: RS256/Auth JWKS, `typ=svc+jwt`, geçerli `kid`, issuer, tek ve doğru hedef audience, tam `sub=svc:<çağıran>`, scope, ham iat/nbf/exp/jti, en fazla 60 saniye ömür ve sözleşmedeki saat toleransı. Alıcı token sub ile zarf producer eşleşmesini ayrıca denetlemeli. Kullanıcı `at+jwt`'si, başka audience veya başka servis kimliği kabul edilmemeli.

Servis token'ı insan moderatör rolünün yerine geçmez. Kararı alan insanın doğrulanması, yetki kapsamı ve audit aktörünün Review'a nasıl güvenilir aktarılacağı Admin/Auth/Review sahiplerinin ortak kararıdır. Sadece body içindeki actorId'ye dayanarak moderasyon yapılmamalı. D4 hesap silme token'ı bu akıştan ayrı tutulmalı.

## 9. Hata ve güvenilirlik beklentileri

Secret boş/geçersiz, token üretimi başarısız, hedef/kapsam reddi veya süresi dolmuş token durumunda teslim fail-closed olmalı; kullanıcı JWT'sine/header'a fallback olmamalı. Token kısa süreli bellekte tutulmalı; gizli değerler loglanmamalı. 401 ile 403 ayrımı korunmalı. Bounded timeout, yenileme ve retry bütçesi gerçek teslim sözleşmesiyle birlikte belirlenmeli. Yetkisizlikte kontrolsüz retry yapılmamalı.

Outbox/relay bu belgede yalnız gelecekteki güvenilirlik gereksinimidir; Review'da bu çalışma kapsamında geliştirilmedi. Auth token üretmesi tek başına Admin'in raporu kalıcı kabul ettiğinin kanıtı değildir.

## 10. Kabul kriterleri

- Test ortamında her onaylı çift doğru audience ve gerekli kapsamla token alır; onaylanmayan çiftler 403, yanlış secret 401 alır.
- Yanlış alg/imza/typ/issuer/audience/producer/scope, eksik zaman claim'i, expired token ve kullanıcı token'ı alıcılarda reddedilir.
- Mevcut Review→User ve Auth→Review D4 regresyonları geçer; yeni imtiyaz yayılımı oluşmaz.
- Gerçek Auth token'ıyla Admin/Review alıcı uçları arasında olumlu ve olumsuz HTTP testleri yapılır; yalnız mock token üretimi teslim kabulü sayılmaz.

## 11. Bağımlılık ve etki

Admin alıcı protokolü ve güven modeli birlikte onaylanmadan REV-07 gerçek teslimi ve REV-10 tam entegrasyonu kapanamaz. REV-06'nın kullanıcı JWT'siyle yerel beğeni akışını engellemez.

## 12. Durum

**SÖZLEŞME BEKLENİYOR / TALEP–ÖNERİ — Yeni hedef yetkileri henüz uygulanmadı.** Auth kodu, ENV, Compose veya ortak ayarlarda değişiklik yapılmadı.

## 13. Karar bekleyen konular

Olay mı senkron komut mu kullanılacak; hangi çiftler hangi kapsamı alacak; Notification üreticisi kim olacak; insan moderatör yetkisi nasıl doğrulanacak; secret'ın mevcut güvenli dağıtımı ve rotasyonu nasıl işletilecek? Nihai kararı ilgili servis sahipleri verir; Review sonradan onaylı sözleşmeye uyarlanır.
