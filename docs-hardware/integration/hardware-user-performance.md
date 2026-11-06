# HW-08 / HW-07 — User ve Performance tüketici bağlantıları

**Sonraki çalışma:** PR #67 cevabına göre REVIEW_AUTHOR tüketicisi tamamlandı;
503 Hardware testi ve gerçek User dahil 25 HTTP kontrolü PASS.
[Güncel yorum entegrasyonu](user-review-integration.md). Aşağıdaki yorum UGC
sözleşmesi açık ifadeleri ilk bağlantı aşamasının tarihçesidir.

Tarih: 2026-11-06. Sorumlu: Alper. Amaç: mevcut Hardware API'lerini frontend
kullanımına hazırlamak. Moderasyon ve bildirim teslimi kapsam dışıdır.

## Kaynak karşılaştırması

User `POST /internal/v1/privacy/decisions` ve Performance
`GET /internal/v1/accounts/{accountId}/devices/{deviceId}` kaynakta uygulanmıştır.
Auth, Hardware'a `user-service → privacy:read` ve hesaba bağlı
`performance-service → devices:verify summary:read` token'ı verebilmektedir.
`docs/api.md` USER bölümündeki “Auth çiftleri henüz tanımlı değil” ve eski Hardware
raporlarındaki “Performance aktif cihaz API'si yok” ifadeleri güncel kodla çelişir;
bu görevde başka sahibin API bölümü değiştirilmemiştir.

## Uygulama

- `UserServiceHardwareVisibilityPolicy`: Hardware'ın doğruladığı viewer ve profil
  sahibini User'a gönderir. Tek ve aynı owner için tipleri doğrulanmış karar
  kabul edilir. Yerel profil/alan/kategori gizliliği aynen uygulanır.
- `PerformanceServiceHardwareSyncDevicePolicy`: owner'a bağlı yeni Auth servis
  token'ıyla aktif cihazı sorar; yalnız 204 izin verir. Her grant, apply ve makbuz
  tekrarında güncel karar alınır; izin kararı veya servis token'ı önbelleğe alınmaz.
- Eski `Unavailable...` sınıfları açık fallback/test kullanımı için korunur;
  Spring component değildir. Üretimde gerçek istemci adapter'ları seçilir.
- HTTP transportu yalnız yapılandırılmış origin'e gider; yönlendirme/retry yok,
  mevcut connect/read timeout, JSON tür kontrolü ve 64 KiB yanıt sınırı vardır.
- Credential istek DTO'sunun toString çıktısı maskelidir; HTTP client/converter
  logger'ları genel DEBUG/TRACE altında INFO'da kalır. Credential, token ve hata
  gövdeleri loglanmaz. Eksik credential, kesinti, yanlış
  durum veya bozuk yanıt erişim açmaz; mevcut güvenli 404 davranışı korunur.

## Ortam

Yeni ENV anahtarı veya migration yoktur. Mevcut `USER_SERVICE_URL`,
`PERFORMANCE_SERVICE_URL`, `AUTH_SERVICE_URL`, `AUTH_HARDWARE_SERVICE_CLIENT_SECRET`
ve `REST_CLIENT_*` anahtarları kullanılır. `.env.example` zaten bu anahtarları
içerir; gerçek ENV dosyaları okunmaz/değiştirilmez. Compose'un Hardware bloğuna
mevcut Hardware credential aktarımı eklenir; Auth ve Hardware'a aynı değer kullanıcı
tarafından verilmelidir. Boş credential temel owner işlemlerini durdurmaz fakat
iki dış policy de reddeder. Gerçek ENV kurulumunun doğrulandığı iddia edilmez.

## Frontend akışı

1. Kullanıcı Auth'ta oturum açar. Hardware işlemleri Gateway cookie/CSRF akışını kullanır.
2. Manuel profil/katalog/bileşen ve owner gizlilik yönetimi mevcut endpoint'lerle çalışır.
3. Paylaşılan profil `GET /api/v1/hardware/profiles/{id}/shared` ile istenir.
   User `allowed=false` dönerse veya yerel gizlilik izin vermezse 404 alınır.
4. Otomatik senkron için kullanıcı Performance'ta sync consent verir, Auth'ta cihaz
   bağlar ve Performance'a cihaz referansını bağlar. Hardware sync consent'inde
   **Auth deviceId** seçilir; Performance device link kimliği kullanılmaz.
5. Hardware consent `profileVersion` ile verilir. Snapshot yalnız Auth'un Hardware
   hedefli cihaz token'ıyla, generation/sequence ve Idempotency-Key ile kabul edilir.
   Tarayıcı donanım taramaz; masaüstü uygulaması MVP sonrasıdır.

## Açık kalan User sınırı

User'ın Social yansıması güvenilir ilan edilene kadar non-owner profil paylaşımı
reddedilir. Yorum UGC kararı, profil HARDWARE ayarından farklı bir sözleşme ihtiyacıdır;
mevcut review audience policy kapalı kalır.
[Kerem'e somut talep ve kabul ölçütleri](user-service-talepleri/user-requests-01.md).
User/Social kodu veya yansıma ayarı bu görevde değiştirilmez.

## Doğrulama

- Yeni `HardwareServiceClientsTest`: **69 PASS**, failure/error/skipped 0. Gerçek loopback
  HTTP ile token hedef/hesap bağlaması, User gövdesi/eşleme, Performance 204/404,
  önbelleksiz iptal, malformed/type/owner/timeout/redirect/oversize/credential negatifleri
  ve TRACE loglarında credential/token bulunmaması doğrulandı.
- Standart sandbox backend denemesi geçici PostgreSQL başlatma/cleanup hatasıyla durdu;
  PASS değildir. İzinli ilk runner tekrarında Compose eklentisi izole ortamda
  keşfedilemedi (`unknown flag: --env-file`); bu da PASS değildir. Yalnız yerel
  görev wrapper'ı kurulu `docker-compose.exe` dosyasını aynı argümanlarla çağırdı.
  Ortak CI runner/kalite kodu değiştirilmedi. Gerçek ENV kullanılmadı.
- `ops/performance-auth-e2e.mjs` artık gerçek Auth/Hardware/Performance/Gateway ve
  ayrı geçici PostgreSQL'lerle Hardware grant→apply→replay→Performance iptalini içerir.
  User/Game servisleri bu fixture'da başlatılmaz. D4 takibi yalnız Performance'a
  yönlendirilir; kalan 15 hedef için sahte ACK verilmez.

| Kontrol | Sonuç |
| --- | --- |
| Hardware JDK25 `clean verify`, son kod, 2026-11-06 07:29:46 +03 | **474 PASS**, failure/error/skipped 0 |
| Hardware merkezi coverage | **PASS**, 1706/1724 = **%98,96**; değişen satırlar 95/95 = %100 |
| Performance JDK25 `clean verify`, 2026-11-06 07:26:25 +03 | **138 PASS**, failure/error/skipped 0; coverage 1239/1276 = **%97,10** |
| Checkstyle/PMD ratchet | **PASS**, Hardware 213 mevcut borç aynı; Performance 0; yeni ihlal 0 |
| İki modülün taze runtime bağımlılık taraması | **PASS**, HIGH/CRITICAL 0; son Hardware JAR ayrıca yeniden tarandı |
| `node ops/performance-auth-e2e.mjs` | **19/19 PASS**; gerçek 4 JAR, 3 PostgreSQL 17 ve Mailpit; 4 süreç/4 container temizlendi |
| `node --test ops/hardware-auth-e2e.test.mjs` | **9/9 PASS**; fixture izolasyon/redaksiyon/freshness regresyonları |
| `node --check ops/performance-auth-e2e.mjs`, `git diff --check` | **PASS** |

Windows/Türkçe repo yolu için mevcut Mockito agent'ı yalnız geçici ASCII dosyadan
test JVM başlangıcına verildi. JaCoCo, testler veya kapılar kapatılmadı. ENV okumayan
`pre-push/ci/backend.mjs` API'si Hardware/Performance modülleriyle kullanıldı; son
logging değişikliği sonrası yalnız değişen Hardware yeniden tam doğrulandı.
Auth JAR eski olduğu için `-DskipTests package` ile E2E'ye hazırlandı; bu **Auth tam
test/kalite kabulü değildir**. Performance üretim endpoint'i zaten yeterli olduğu
için kaynak kodu değişmedi; gerçek tüketici kabulü genişletildi.

Yerel, Git dışı kanıtlar: `logs/hardware-services-backend.log`,
`logs/hardware-services-final.log`, `logs/hardware-performance-runtime.log`,
`logs/hardware-services-auth-package.log`. Full dev, bütün backend, frontend ve
Sonar bu görevde çalıştırılmadı; `SAFE TO COMMIT`/PDA kapanışı iddiası yoktur.

Ortak DEV/TLS/tarayıcı, gerçek User/Social paylaşımı, masaüstü uygulaması ve bütün
hesap silme Saga'sı bu görevde VERIFIED sayılmaz.
