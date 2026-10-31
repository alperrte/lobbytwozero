# AUTH–REVIEW entegrasyon talebi

## 1. Talep eden servis

`review-service` — Hamza. Bu belge Review'ın ihtiyacını bildirir; Auth sahibine kesin geliştirme emri değildir.

## 2. Hedef servis

`auth-service` — Kerem. Nihai yetki/istemci tasarımı Auth sahibince belirlenir.

## 3. İlgili task

REV-07, REV-10 ve REV-12'nin bekleyen Game→Review sunucu entegrasyonu. Bildirim üreticisi Review seçilirse koşullu bildirim yetkisi de REV-10 ile ilişkilidir. REV-06 ve REV-12'nin tamamlanan Review kullanıcı API kabulü bu talepten bağımsızdır.

## 4. Talebin amacı

Gelecekte onaylanacak rapor teslimi ve moderasyon geri bildirimini, kullanıcı token'ını servis kimliği yerine kullanmadan, hedefe bağlı ve en az yetkili servis token'ıyla gerçekleştirmek.

Faz 3 eki: Game'in Review toplu puan özetini sunucudan kendi servis kimliğiyle okuyabilmesi. Kullanıcı mevcut JWT güvenliğinin korunmasını ve yeni servis-token erişiminin bu fazda uygulanmamasını açıkça istedi.

## 5. Repository üzerinde doğrulanan mevcut durum

- [Auth application.properties](../../backend/services/auth-service/src/main/resources/application.properties): `review-service` istemci kaydı ve `${AUTH_REVIEW_SERVICE_CLIENT_SECRET:}` referansı var; **yalnız** `targets.user-service=events:ingest` tanımlı. Gerçek secret değeri/dağıtımı incelenmedi.
- Aynı dosyada community→admin ve community→notification `events:ingest` örnekleri var; bunlar Review'a yetki vermez. Admin'in mevcut hedefi community; admin→review tanımlı değil.
- [ServiceClientConfig](../../backend/services/auth-service/src/main/java/com/ltz/auth/infrastructure/config/ServiceClientConfig.java): hedef kapsam allowlist'i `events:ingest`, `blocks:check`, `privacy:read`, `signals:read`; tanımsız özel kapsam uydurulamaz. `account:erase` ayrı D4 sözleşmesidir ve bu iş için kullanılamaz.
- Mevcut `POST /internal/v1/service-tokens`, hedef audience seçimini destekler. Kayıtsız hedef 403, geçersiz istemci 401 davranışı [Auth servis sözleşmesinde](../../docs-auth/servis-sozlesmeleri.md) belgeli.
- Kullanıcı JWT/JWKS ve Auth→Review D4 silme entegrasyonu zaten mevcut; yeniden kurulması talep edilmiyor.
- Game→Review hedef/kapsamı veya bu amaçlı Game istemci kaydı application.properties içinde yok. Review kullanıcı özet API'leri servis token'ı kabul etmez. Mevcut kapsam allowlist'inde özel puan özeti okuma izni yoktur; D4 silme izni kullanılamaz.

## 6. Eksik gereksinimler

- Admin ile alıcı protokolü onaylanınca Review'ın yalnız rapor olayını Admin'e iletebilmesine uygun hedef/kapsam çifti.
- Admin'in onaylı rapor durum/karar mesajlarını yalnız Review'a iletebilmesine uygun ters hedef/kapsam çifti.
- **Koşullu:** moderasyonun uygulandığı bilgisini Notification'a Review gönderecekse Review→Notification için ayrı hedef/kapsam. Üretici Admin seçilirse gereksiz Review izni verilmemeli.
- Mevcut Review→User izni korunmalı; bu taleple genel admin yetkisi, başka hesap okuma veya tüm hedeflere erişim istenmiyor.

## 7. Önerilen sözleşme — kesinleşmedi

Faz 3 ek talebi: Game→Review için yalnız puan özeti okumaya izin veren hedef/kapsam çifti. `rating-summaries:read` yalnız örnek isim önerisidir; mevcut allowlist'te yoktur ve uygulanmadı. Mevcut izinlerden birinin uygunluğu veya yeni kapsam gerekip gerekmediği Auth sahibince belirlenmeli. Mevcut token edinme protokolünün kullanımı ve Review'da dar okuma alıcısı karşılıklı onay bekler. Kullanıcı API'si veri modeli için [Game talebi](game-review-integration.md) esas alınabilir; servis-token alıcısı bugün mevcut değildir.

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

Game okumasında da token edinimi/yenilenmesi bounded olmalı; yanlış yetki için kullanıcı token'ına veya account:erase kapsamına fallback yapılmamalı. Salt okunur puan sorgusu için outbox gerekmez.

Secret boş/geçersiz, token üretimi başarısız, hedef/kapsam reddi veya süresi dolmuş token durumunda teslim fail-closed olmalı; kullanıcı JWT'sine/header'a fallback olmamalı. Token kısa süreli bellekte tutulmalı; gizli değerler loglanmamalı. 401 ile 403 ayrımı korunmalı. Bounded timeout, yenileme ve retry bütçesi gerçek teslim sözleşmesiyle birlikte belirlenmeli. Yetkisizlikte kontrolsüz retry yapılmamalı.

Outbox/relay bu belgede yalnız gelecekteki güvenilirlik gereksinimidir; Review'da bu çalışma kapsamında geliştirilmedi. Auth token üretmesi tek başına Admin'in raporu kalıcı kabul ettiğinin kanıtı değildir.

## 10. Kabul kriterleri

- Game→Review çifti onaylanırsa yalnız doğru sub/aud/typ/scope ile özet okunur; diğer endpointlere yetki yayılmaz. Yanlış kapsam, yanlış servis, süre sonu ve kullanıcı token'ının servis kimliği yerine kullanılması reddedilir. Gerçek Auth token'ıyla Game/Review entegrasyonu ayrıca doğrulanır; bu fazda NOT VERIFIED.

- Test ortamında her onaylı çift doğru audience ve gerekli kapsamla token alır; onaylanmayan çiftler 403, yanlış secret 401 alır.
- Yanlış alg/imza/typ/issuer/audience/producer/scope, eksik zaman claim'i, expired token ve kullanıcı token'ı alıcılarda reddedilir.
- Mevcut Review→User ve Auth→Review D4 regresyonları geçer; yeni imtiyaz yayılımı oluşmaz.
- Gerçek Auth token'ıyla Admin/Review alıcı uçları arasında olumlu ve olumsuz HTTP testleri yapılır; yalnız mock token üretimi teslim kabulü sayılmaz.

## 11. Bağımlılık ve etki

Admin alıcı protokolü ve güven modeli birlikte onaylanmadan REV-07 gerçek teslimi ve REV-10 tam entegrasyonu kapanamaz. REV-06'nın kullanıcı JWT'siyle yerel beğeni akışını engellemez.

Game servis kimliği eksikliği REV-12 Review backend kabulünü engellemez; Game→Review gerçek tüketimi ve REV-10 kapanışı bekler. Review kullanıcı JWT'sini Game servis kimliği olarak kullanmayacak.

## 12. Durum

**SÖZLEŞME BEKLENİYOR / TALEP–ÖNERİ — Yeni hedef yetkileri henüz uygulanmadı.** Auth kodu, ENV, Compose veya ortak ayarlarda değişiklik yapılmadı.

## 13. Karar bekleyen konular

Faz 3: Game istemci kimliği, hedefe bağlı dar kapsam, allowlist uyarlaması, güvenli dağıtım ve Review okuma alıcısının yolu netleştirilmeli. Yeni scope/ENV/token bu fazda üretilmedi.

Olay mı senkron komut mu kullanılacak; hangi çiftler hangi kapsamı alacak; Notification üreticisi kim olacak; insan moderatör yetkisi nasıl doğrulanacak; secret'ın mevcut güvenli dağıtımı ve rotasyonu nasıl işletilecek? Nihai kararı ilgili servis sahipleri verir; Review sonradan onaylı sözleşmeye uyarlanır.
