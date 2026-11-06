# hardware-service: notlar (elle tutulur)

> Bu dosya **elle** yazılır ve otomatik üretilen `README.md` tarafından ezilmez. Sahip: Alper.
> Servise özgü bilinen durumlar, test/ortam notları, ertelenen güvenlik kararları buraya yazılır.

## Bilinen durumlar

- 2026-11-06: Üretim policy'leri artık `UserServiceHardwareVisibilityPolicy` ve
  `PerformanceServiceHardwareSyncDevicePolicy` istemcileridir. Her dış karar için
  yeni Auth servis token'ı alınır; bilinmeyen/erişilemeyen karar reddedilir.
  Credential DTO'su maskelidir; HTTP client/converter INFO pin'leri ve TRACE log
  regresyonu vardır. 474 Hardware testi ve 19 gerçek dört servisli HTTP kontrolü PASS.
  User güvenilir Social yansıması ve yorum UGC sözleşmesi açık kalır.
  [Güncel kanıt](../../docs-hardware/integration/hardware-user-performance.md).

- 2026-10-29: Kullanıcı JWT ömrü Hardware'da en çok 60 sn, saat toleransı 30 sn'dir.
  Auth TTL'i yükseltilirse Hardware daha uzun token'ı reddeder. Ayrı `dev+jwt` doğrulaması
  yalnız snapshot POST'unda çalışır; `svc:` kuralı değişmez.
- 2026-10-29 tarihçesi: Performance aktif cihaz/sahiplik sözleşmesi kullanıcı kararıyla ayrı iş olarak bırakıldı.
  `UnavailableHardwareSyncDevicePolicy` üretimde deny eder; cihaz Auth doğrulaması apply başarısı değildir.
- Windows Türkçe depo yolunda Mockito self-attach agent JAR'ını açamayabilir.
  Bu koşuda yalnız test JVM'leri için mevcut Mockito agent'ı ASCII geçici dizinden
  `JAVA_TOOL_OPTIONS=-javaagent:<geçici-yol>` ile başlatıldı. JaCoCo ve testler kapatılmadı.
  Gerçek ENV dosyası okunmadı/değiştirilmedi. [Kanıt ve sınırlar](../../docs-hardware/integration/auth/auth-integration-06.md).
