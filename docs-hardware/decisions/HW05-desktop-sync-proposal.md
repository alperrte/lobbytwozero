# HW-05 masaüstü tarama ve izinli sync modeli — APPROVED

Tarih 2026-10-21; sorumlu Alper. Master §0.4/§3.7 ve SECURITY.md §12/§14/§19.
Kullanıcı HW-04 PDA kapanışını ve HW-05 geçişini bildirdi. f558a69 commit'i
57 dosyalık teslim kapsamıyla eşleşir; başlangıç çalışma ağacı temiz, yerel tracking
origin/feature/hardware-service ile ahead/behind 0/0. Git yazma işlemi yapılmadı.
Kullanıcı modeli ve güvenli ertelemeyi ayrı ayrı onayladı.
Bu belge uygulama veya gerçek cihaz bağlantısı kanıtı değildir.

## 1. Kapsam ve somut HTTP sözleşmesi

Yalnız hardware backend, önce HW-06 sonra HW-07. Masaüstü tarayıcı/istemci,
PERF-01 cihaz eşleştirme veya başka servis geliştirilmez. Tüm yolların prefix'i
/api/v1/hardware; beş yeni protected endpoint önerilir:

- POST /profiles/{profileId}/sync/preview — HW-06: owner + CSRF, tek istek için
  processingConsent=true açık onayı; donanım payload'unu doğrular/normalize eder.
  Bileşenleri, sürekli sync iznini veya ham payload'u kalıcı kaydetmez. Kaynak
  DESKTOP_SYNC atanmaz; bu bir otomatik tarama/cihaz doğrulama kanıtı değildir.
- GET /profiles/{profileId}/sync-consent — HW-07: owner; izin ve son sync metadata'sı.
- PUT /profiles/{profileId}/sync-consent — owner + CSRF + profileVersion,
  deviceId ve accepted=true. DeviceId yalnız seçim girdisidir; güvenilir dış
  policy cihazın aktif ve owner'a bağlı olduğunu ayrıca doğrulamalıdır.
- DELETE /profiles/{profileId}/sync-consent?profileVersion=... — owner + CSRF;
  izni iptal eder, sonraki teslim/retry'ları durdurur. Var olan donanımı silmez.
- POST /profiles/{profileId}/sync/snapshots — yalnız ayrı typed cihaz principal'ı,
  hardware:sync:write uygulama izni + CSRF + aktif cihaz policy'si + aynı owner/
  device/generation için GRANTED yerel izin. Browser actor'ı bu izne sahip olsa
  bile cihaz principal'ı yerine geçmez. Idempotency-Key zorunlu UUID header.

Kimlik/cihaz güveni client userId/deviceId/X-Role/X-Device-Id alanlarından türetilmez.
Cihaz principal'ı sunucunun doğruladığı user/device UUID bağlamıdır; gerçek token,
scope/claim/issuer/cookie protokolü uydurulmaz. Üretimde Auth doğrulayıcı yoktur;
ayrı cihaz policy adapter'ı da deny eder. İzin veren kimlik/policy yalnız testlerdedir.
Yeni anonim endpoint, ENV, Compose, dependency veya gateway değişikliği yoktur.

## 2. Tarama payload'u ve veri minimizasyonu

Scan v1: schemaVersion=1, capturedAt UTC, 1..32 bileşen. CapturedAt son 24 saat
içinde ve en fazla 5 dakika gelecekte olmalıdır. Her bileşen için opaque UUID
componentKey (seri numarası değildir), mevcut 12 kategoriden biri, modelName
1..160, opsiyonel brandName 1..100, quantity 1..128 ve mevcut sekiz typed teknik
alan kullanılır. ComponentKey bir snapshot'ta tekildir. Marka zorunlu değildir.
Metinler ham/NFKC sonrası kontrol/HTML ayraçlarına ve uzunluğa karşı doğrulanır.
Scanner katalog UUID'si göndermez; productId/brandId bağlanmaz, genel model metni
snapshot olarak saklanır. Katalog dışı/markasız model desteklenir.

Seri numarası, MAC, hostname, BIOS kimliği, disk dosyaları, FPS/sıcaklık/telemetri,
serbest JSON veya bilinmeyen alan kabul edilmez. Gövde 16 KiB ile sınırlıdır.
Ham tarama/request body loglanmaz, receipt'e kopyalanmaz; owner DTO'ları no-store/private.
Payload değerleri doğrulansa bile fiziksel donanım attestation'ı sayılmaz.

## 3. Kaynak, izin ve güncelleme kuralları

Profilin source=MANUAL değeri oluşturulma kökenidir ve korunur. Kabul edilen
otomatik bileşenleri sunucu DESKTOP_SYNC yapar; client source atayamaz.
Bir profil aynı anda yalnız bir cihaza izin verir. Her grant yeni generation UUID
ve sıfırlanan sequence başlatır; aktif izni değiştirmek için önce revoke gerekir.
Grant/revoke ve apply mevcut parent profil kilidi + profileVersion ile seri olur.
Revocation sonrası yeni payload ve eski receipt retry'ı reddedilir; yeniden
grant eski generation/sequence yetkisini geri vermez. Profil silme consent ve
receipt'leri CASCADE ile siler; başka profilleri etkilemez.

Yeni snapshot yalnız DESKTOP_SYNC kümesini anahtara göre ekler/günceller;
snapshot'ta olmayan eski DESKTOP_SYNC satırlarını kaldırır. MANUAL satırları,
profil adı/türü/gizlilik tercihleri değişmez. Boş snapshot kabul edilmez.
Apply sonrası toplam bileşen kaydı en fazla 200; yeni otomatik küme en fazla 32.
Mevcut MANUAL kayıtlarına yeni bir genel kota getirilmez; toplam limiti aşan sync
tümden reddedilir. Son successful capturedAt/sequence/syncedAt izin metadata'sındadır.

Owner mevcut manuel PUT ile bir DESKTOP_SYNC bileşenini değiştirirse sunucu
kaynağı MANUAL'a çevirip sync anahtarını ayırır; sonraki snapshot bu manuel kaydı
ezmez. Sonraki snapshot aynı tarama anahtarı için ayrı otomatik kayıt oluşturabilir.
Manuel DELETE mümkündür; izin sürerken yeni snapshot aynı otomatik bileşeni yeniden
getirebilir. Kalıcı otomatik güncellemeyi durdurmak için revoke kullanılır.

## 4. Idempotency, sıralama ve sınırlar

Apply body: profileVersion, consentGeneration, sequence (>=1) ve scan v1.
Her generation'da sequence kesin artar; eski sıra ve eski capturedAt 409 olur.
Parent kilidi owner düzenleme/revoke/apply yarışını korur. Tek transaction içinde
bütün snapshot + profil version + son sıra metadata'sı + receipt kaydedilir.

Receipt anahtarı profil + Idempotency-Key'dir; fingerprint normalize payload'u,
hedefi, user/device/generation/sequence ve beklenen profileVersion'ı kapsar.
Aynı anahtar/hash 24 saat içinde önceki asgari yanıtı döndürür, ikinci mutasyon yoktur;
farklı hash 409. Replay öncesi güncel kimlik, cihaz sahipliği ve aktif consent tekrar
kontrol edilir. Saklanan yanıt yalnız version/zaman/sıra ve değişiklik sayaçlarıdır.
Receipt süresi geçse de kalıcı lastSequence eski paketlerin tekrar uygulanmasını önler.

Profil başına en fazla 500 canlı receipt/24 saat; yeni kayıt limiti aşarsa 429.
Saatlik job süresi dolan receipt'leri temizler (normal saklama yaklaşık 24–25 saat).
Preview/grant/revoke user başına 20/dakika; apply user 30/dakika + device 10/dakika;
socket peer tüm yeni mutasyonlarda 120/dakika. Fixed-minute DB bucket'ları mevcut
hardware_write_limits altyapısını kullanır; ham IP saklanmaz, XFF'a güvenilmez.
Sayaçlar başarılı transaction'a katılır; rollback denemeleri kota tüketmez.
Gerçek reverse-proxy istemci IP zinciri NOT_VERIFIED kalır.

## 5. Şema ve sıralı uygulama

HW-06 stateless preview/DTO/validation/test/API kaydıyla tamamlanır; DB değişmez.
HW-07 için son boş hardware sürümü V10__add_hardware_desktop_sync.sql kullanılacak:
consent, bounded receipt, bileşen source CHECK genişletmesi ve nullable sync key/
partial unique indeksleri. UUID, UTC timestamptz, version, FK/CASCADE ve CHECK
kuralları uygulanır; V1–V9 değiştirilmez. V10 test edildikten sonra yeniden yazılmaz.
İlave migration gerekirse numara uydurulmaz, ekip kararı istenir.

## 6. Açık gerçek entegrasyonlar ve onay ihtiyacı

Gerçek Auth DEFERRED_AUTH kalır. Auth cihaz credential/scope/claim doğrulaması,
PERF-01 eşleştirme/iptal/aktif cihaz sahipliği API'si ve gerçek masaüstü E2E için
onaylı sözleşme yoktur. HW-05'e özgü DEFERRED_CONTRACT / NOT_VERIFIED önerilir;
production cihaz policy deny, adapter'lar test-only izin ile yerel kuralları sınar.
Uydurma RestClient URL/JSON, user token'ından cihaz kimliği çıkarma veya cihaz
eşleştirme sistemi hardware içinde yapılmaz. Gerçek bağlantılar sonraki talimatla
eklenir; MVP sonrası istemci entegrasyonu yapılmış sayılmaz.

Onay alternatifi: cihaz API/güven sözleşmesi gelene kadar HW-07 kabulünü bloke
tutmak. Auth veya sahiplik korumasını açmak seçenek değildir.
Yerel kriterler ve taze full dev + scan dev PASS sonrasında onaylı ertelemelerle
READY_FOR_PDA mümkündür; production-ready veya gerçek cihaz E2E denmez.
Beşinci ana taskın teslimiyle toplu HARDWARE_SERVICE_FINAL_REPORT.md oluşturulur.
