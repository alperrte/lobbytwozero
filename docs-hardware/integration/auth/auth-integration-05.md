# HW-AUTH-05 — İkinci aşama talep karşılaştırması ve Hardware karşılığı

Tarih: 2026-10-29. Sorumlu: Alper. Görevler: HW-06/HW-07 ve Hardware–Auth entegrasyonu.
Çalışan testlerin sonuçları [06 numaralı kayıtta](auth-integration-06.md) ayrıca belirtilir.

## Talep–cevap–kod karşılaştırması

| İlk talep | Auth cevabının koddaki karşılığı | Sonuç ve sınır |
| --- | --- | --- |
| [01 — Hesap/oturum iptali](entegrasyon-v1/auth-service-talepleri/auth-requests-01.md) | `application.properties` kullanıcı TTL varsayılanını 60 sn yapıyor; `SessionService` logout/oturum iptalini Auth'ta uyguluyor. Yerel tüketiciye introspection veya genel revoke akışı eklenmemiş. `AccountStatusChanged` outbox yalnız yönetici durum değişimleri için, varsayılan hedef listesi boş. | İlk olay/cache/restart kabul matrisi uygulanmış sayılmaz. Auth'un sunduğu çözüm **60 sn token + 30 sn tolerans**, yani exp sonrası en çok 30 sn erişimdir. Hardware eskiden 15 dakikalık token'ı da kabul ediyordu; bu aşamada üst sınır 60 sn'ye indirildi. Auth TTL yapılandırması yükseltilirse Hardware token'ı reddeder; sessizce daha uzun iptal gecikmesi oluşmaz. |
| [02 — Ayrı cihaz kimliği](entegrasyon-v1/auth-service-talepleri/auth-requests-02.md) | `DeviceController`, `DeviceCredentialService`, `DeviceTokenIssuer`, V67 cihaz credential migration'ı mevcut. RS256/JWKS, `typ=dev+jwt`, `aud=hardware-service`, `sub=deviceId`, `account_id`, scope, jti ve 60 sn ömür kaynakta var. Anahtar yalnız bağlama yanıtında, DB'de özeti tutuluyor; limitler 5 etkin cihaz/24 saatte 10 bağlama, credential 365 gün. | **Auth üretici tarafı karşılanmış.** Hardware tüketici doğrulaması bu aşamada eklendi. Performance aktif cihaz/sahiplik sözleşmesi henüz yok; bu nedenle gerçek consent→apply başarı akışı tamamlanmış değildir. Kullanıcı bu bağımlılığın açık tutulmasını seçti. |
| [03 — Gerçek D4 kabulü](entegrasyon-v1/auth-service-talepleri/auth-requests-03.md) | Auth'ta 14 gün grace period mevcut; `AUTH_DELETION_GRACE_PERIOD=PT0S` yalnız geçici fixture sürecine verildi. Auth 16 hedefi kalıcı görev/ACK ile takip ediyor. Social/Game/Library/Review/Hardware iç DELETE controller'ları mevcut. | Repoda **11 eksik uç** var. İzole test yalnız Hardware katılımcısını çalıştırdığı için **1 CONFIRMED + 15 PENDING** bekler; hazır diğer dört servis de o fixture'da kapalıdır. Sayıyı 11'e indirmek veya hedefleri azaltmak sahte kabul olur. Ortak DEV ve tüm Saga ayrı, henüz doğrulanmış değildir. |
| [04 — DEBUG/TRACE gizliliği](entegrasyon-v1/auth-service-talepleri/auth-requests-04.md) | Auth `application.properties` içindeki 9 logger açıkça INFO'ya sabitlenmiş. `LoggingPrivacyPinTest` bu ayarları denetliyor. Google/Steam testleri hassas sentetik değerlerin loglarda bulunmamasını kontrol ediyor. | Kaynak düzeltmesi mevcut. Bu oturumdaki DEBUG ve test tekrarlarının gerçek sonucu kalite kaydındadır; önceki 22/22 beyanı otomatik olarak yeni kanıt sayılmaz. |

Kaynak cevaplar: [entegrasyon özeti](../../../docs-auth/entegrasyon/hardware-service.md),
[dört ayrıntılı cevap](../../../docs-auth/verilen-cevaplar/hardware-service/README.md),
[API kataloğu](../../../docs/api.md#auth--masaüstü-cihaz-kimliği-hardware-service).
Eski genel belgelerde 300 sn/15 dk veya “JWT henüz yok” ifadeleri bulunur; güncel kaynak kodu
ve Auth'un bu cevabı esas alınır. Bu çelişki, eski belgeler gerçekleşmiş güncel durum gibi kullanılmadan kayda geçirildi.

## Hardware uygulaması

`HardwareDeviceJwtValidator` kullanıcı ve servis doğrulayıcılarından ayrıdır.
RS256 imzası ve `kid`, mevcut sınırlı JWKS transportu üzerinden kontrol edilir. Başlık türü
tam `dev+jwt`, issuer yapılandırılmış Auth issuer'ı, audience içinde `hardware-service`,
`sub` ve `account_id` kanonik UUID, `jti` dolu, scope string'inde tam `hardware:sync:write`
aranır. Ham `exp/nbf/iat` zorunludur, ömür en çok 60 sn ve saat toleransı 30 sn'dir.

Principal yalnız `AuthenticatedHardwareDevice(userId=account_id, deviceId=sub)` olur.
Token'a rol/başka scope eklemek kullanıcı veya yönetici yetkisi üretmez.
Yalnız `POST /api/v1/hardware/profiles/{profileId}/sync/snapshots` cihaz doğrulayıcısını kullanır.
Kullanıcı/ADMIN bu uçta 403; cihaz token'ı kullanıcı ve D4 uçlarında 401 alır.
`svc:` subject kuralı ve D4 yetki/sahiplik kontrolü korunur.

Başarılı token doğrulamasından sonra hesap silme fence'i ve
`HardwareSyncDevicePolicy` yeniden kontrol edilir. Üretimdeki
`UnavailableHardwareSyncDevicePolicy` deny eder: grant ve geçerli cihazla apply 404 alır,
consent/receipt oluşturulmaz. Buna rağmen mock policy ile çalışan yerel snapshot iş
kuralları gerçek Performance veya masaüstü E2E başarısı diye sunulmaz.

17 dakikalık onaylı D4 fence saklama süresi bu görevde değiştirilmedi; 60 saniyelik
token sınırını kapsar. Yeni migration, dependency, ENV dosyası veya public endpoint yoktur.

## Deneme ve kurulum

- Ortak yerel Auth kurulumu için kullanıcı bir kez `node ops/generate-auth-dev-keys.mjs`
  çalıştırır. Bu komut gerçek yerel ENV dosyasına yazabildiğinden bu görevde ajan tarafından
  çalıştırılmadı. İzole E2E anahtarlarını bellekte üretir ve bu adıma ihtiyaç duymaz.
- Auth/Gateway/Hardware JAR'ları güncel kaynaktan paketlenmelidir. Fixture başlamadan üçü de
  freshness kontrolünden geçer; eski/eksik JAR ile gerçek entegrasyon PASS verilmez.
- `node ops/hardware-auth-e2e.mjs`: kayıt/giriş, kullanıcı kimliği, gerçek cihaz bağlama/token/iptal,
  Hardware'da Performance yokken apply reddi ve gerçek Auth worker → Hardware D4 kontrolü.
  Kullanılan cihaz iptal edilir, yeni test hesabı üretilir; 5/10 limitleri aşılmaya çalışılmaz.
- Cihaz iptalinden sonra yeni token alınamaz. Önceden üretilen token 60 sn + 30 sn tolerans
  bitene kadar kriptografik olarak geçerli olabilir; anlık cihaz iptal yayılımı yoktur.

## Kalan bağımlılıklar

**DEFERRED_CONTRACT / PERF-01/02:** Performance aktif cihaz envanteri, owner/active/revoked
kararının sürümlü API'si ve kesinti/revoke davranışı Alper'in ayrı görevidir. Auth'un GET devices
uç listesi yerine kullanılmadı; kullanıcı token'ı cihaz kanıtı yapılmadı.

**NOT VERIFIED:** Ortak Compose DEV, TLS/tarayıcı kabulü ve bütün 16 katılımcılı Saga.
11 eksik silme endpoint'i ilgili servis sahiplerine aittir; bu görev kapsamına alınmadı.
Auth'tan beklenen yeni bir geliştirme talebi saptanmadı. Ortak DEV kabulü
[entegrasyon doğrulama kaydında](auth-integration-06.md#ortak-dev-doğrulama-takibi) izlenir.
