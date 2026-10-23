# HW01-QG-01 — Sonar Maven test ortamı düzeltmesi (ONAYLANDI)

Kullanıcı 2026-10-21 tarihinde “Dar runner düzeltmesini uygula” dedi. Kapsam yalnız
aşağıdaki ortak kalite dosyalarıdır; uygulama/ENV/Compose değişikliği onaylanmadı ve yapılmadı.

## Doğrulanan sorun

Token kurulduktan sonraki `sonar.cmd scan dev` erişim aşamasını geçti. Bütün backend
testleri ise 16 servisin contextLoads hatası nedeniyle başarısız oldu. PostgreSQL kapalıyken
connection refused; mevcut `ltz-dev-postgres-1` başlatıldıktan sonra kalan testlerde
authentication failed görüldü. Hardware'in izole PostgreSQL testleri 58 PASS kaldı.

Kaynak kod kanıtı: `lib/profile-env.mjs` içindeki `loadProfileEnv`, seçilen profilden
yalnız SONAR_* alanlarını yükler. POSTGRES_* değerlerini Maven'e aktarmaz. Tarama ise
bütün modüllerin başarılı `clean verify` çıktısını zorunlu tutar. Şablonda ayrıca
POSTGRES_HOST/PORT container içi, POSTGRES_HOST_PORT host erişimi içindir. Host Maven
testleri container içi `postgres:5432` adresini kullanmamalıdır.

Gerçek profil/token/parola dosyaları açılmadı veya değerleri yazdırılmadı. Eksik ayarlar
kaynak kod/şablon ve hata türleriyle teşhis edildi; mevcut secret değerleri varsayılmadı.

## Önerilen en küçük değişiklik

- Yalnız ortak kalite altyapısında, Maven test alt süreci için ayrı bir ortam hazırlayıcı.
  Mevcut profilin yalnız gerekli POSTGRES_USER/PASSWORD/HOST_PORT ve DB_* adlarını
  içeride okuyacak; değerleri loga/sohbete taşımayacak.
- Host üzerinde çalışan Maven için POSTGRES_HOST=127.0.0.1 ve
  POSTGRES_PORT=profilin mevcut POSTGRES_HOST_PORT değeri kullanılacak.
  Container/uygulama/Compose ortamı değişmeyecek.
- Mevcut `loadSonarEnv` kapsamı korunacak; Docker Sonar up/status işlemleri bu
  host-test uyarlamasından etkilenmeyecek. PATH/NODE_OPTIONS gibi alanlar profil
  dosyasından yüklenmeyecek; profil içerikleri shell olarak çalıştırılmayacak.
- POSTGRES_PASSWORD, mevcut SONAR_TOKEN/SONAR_DB_PASSWORD gibi redaction listesine
  eklenecek. Terminalden açıkça verilmiş test ayarlarının önceliği belgelenip test edilecek.
- `lib/profile-env.mjs`, `sonarqube/sonar.mjs`, ilgili regresyon testleri ve
  `sonarqube/sonarqube.md` değişecek. Yeni ENV anahtarı/bağımlılık, ENV dosyası,
  Compose, servis kodu veya migration değişikliği olmayacak.
- Sentetik fixture'larla kapsam, host/container ayrımı, profil izolasyonu ve secret
  maskeleme test edilecek. Ardından kullanıcıca onaylı gerçek tarama tekrar çalıştırılacak.

Onay sonrası kodlandı; 44 sentetik regresyon testi ve gerçek Sonar taraması PASS.
Terminalden açıkça tanımlanan test bağlantı ayarları önceliklidir. Host portu 1..65535
aralığında doğrulanır; kullanıcı/parola eksikse eski raporlar temizlenmeden durulur.

Regresyon kanıtı: `node --test sonarqube/sonar.test.mjs pre-push/menu.test.mjs
pre-push/quality.test.mjs pre-push/runtime-checks.test.mjs ops/env.test.mjs`:
**44 PASS**, 0 failure/skipped, exit 0. Gerçek profil dosyaları test fixture'ı olarak
kullanılmadı; sentetik geçici dosyalar kullanıldı. Gerçek scan dev tekrar başlatıldı.

Son gerçek sonuç: bütün backend 76 test PASS; Security/Reliability/Maintainability A,
Quality Gate PASS, exit 0. Son analiz 9be6f62f-5bf5-44f8-9d1d-53ca6d9350fc.
Karar uygulanıp doğrulandı; açık kalite blokajı kalmadı.

## Alternatif

Kullanıcı, mevcut uygulama DB ayarlarını tarama terminalinin ortamına kendisi yükler
(host bağlantısı 127.0.0.1 / POSTGRES_HOST_PORT). Mevcut betik terminal ortamını Maven'e
aktarır. Gerçek değerler sohbete paylaşılmaz; ajan `.env.dev` dosyasını açmaz.
Sonar'ı veya diğer servis testlerini atlamak kabul edilen bir alternatif değildir.
